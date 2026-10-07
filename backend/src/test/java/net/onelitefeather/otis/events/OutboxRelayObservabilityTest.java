package net.onelitefeather.otis.events;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.micrometer.core.instrument.Gauge;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OutboxEvent;
import net.onelitefeather.otis.database.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec "Event pipeline is observable": the pending gauge, the relay span and the single WARN per failure burst. */
@MicronautTest(environments = "test", transactional = false)
@Property(name = "otis.events.enabled", value = "true")
@Property(name = "otis.events.publisher", value = "fake")
@Property(name = "otis.events.relay.initial-delay", value = "1h")
class OutboxRelayObservabilityTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final String SECRET = "payload-secret-marker";

    // instance field on purpose: every test gets its own SDK and nothing is registered globally
    @RegisterExtension
    final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

    @Inject
    OutboxEventRepository outbox;

    @Inject
    DataSource dataSource;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ManualClock clock = new ManualClock(START);
    private final RecordingEventPublisher publisher = new RecordingEventPublisher();
    private final Logger relayLogger = (Logger) LoggerFactory.getLogger(OutboxRelay.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        OutboxRows.clear(dataSource);
        logs.start();
        relayLogger.addAppender(logs);
        relay = new OutboxRelay(outbox, publisher, clock, otel.getOpenTelemetry(), meters, "relay-a");
    }

    @AfterEach
    void tearDown() {
        relayLogger.detachAppender(logs);
        logs.stop();
        relay.close();
    }

    private void store(int count) {
        for (int i = 0; i < count; i++) {
            outbox.save(new OutboxEvent(UUID.randomUUID(), UUID.randomUUID().toString(),
                    "net.onelitefeather.otis.account.linked", "{\"secret\":\"" + SECRET + "\"}", START.plusSeconds(i)));
        }
    }

    private List<ILoggingEvent> warnings() {
        return logs.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
    }

    private SpanData relaySpan() {
        List<SpanData> spans = otel.getSpans();
        assertEquals(1, spans.size(), "exactly one span per run, got " + spans.stream().map(SpanData::getName).toList());
        return spans.getFirst();
    }

    // --- gauge ---------------------------------------------------------------------------------------------

    @Test
    void theGaugeReportsTheUnpublishedRowsWithoutTags() {
        store(3);
        publisher.failWith(new EventPublishException("broker down"));

        relay.run();

        Gauge gauge = meters.find("otis.outbox.pending").gauge();
        assertNotNull(gauge, "gauge otis.outbox.pending must be present");
        assertEquals("Outbox events not yet published to Kafka", gauge.getId().getDescription(), "description");
        assertTrue(gauge.getId().getTags().isEmpty(), "the gauge has no tags");
        assertEquals(3.0, gauge.value(), "all three rows are still pending");
    }

    @Test
    void theGaugeDropsToZeroOnceTheRowsArePublished() {
        store(2);

        relay.run();

        assertEquals(0.0, meters.get("otis.outbox.pending").gauge().value(), "nothing pending any more");
    }

    @Test
    void closingTheRelayRemovesTheGauge() {
        relay.close();

        assertNull(meters.find("otis.outbox.pending").gauge(), "the gauge is removed");
    }

    @Test
    void noOpenTelemetryMetricIsCreated() {
        store(1);

        relay.run();

        assertTrue(otel.getMetrics().isEmpty(), "the backlog is a Micrometer metric only");
    }

    // --- span ----------------------------------------------------------------------------------------------

    @Test
    void aRunCreatesOneInternalSpanWithClaimedAndPublishedCounts() {
        store(2);

        relay.run();

        SpanData span = relaySpan();
        assertEquals("outbox.relay", span.getName(), "span name");
        assertEquals(SpanKind.INTERNAL, span.getKind(), "span kind");
        assertEquals(2L, span.getAttributes().get(OutboxRelay.CLAIMED), "claimed");
        assertEquals(2L, span.getAttributes().get(OutboxRelay.PUBLISHED), "published");
        assertNotNull(span.getStatus(), "status");
        assertFalse(span.getStatus().getStatusCode() == StatusCode.ERROR, "a healthy run is not an error");
    }

    @Test
    void aBrokerFailureIsNotASpanError() {
        store(1);
        publisher.failWith(new EventPublishException("broker down"));

        relay.run();

        SpanData span = relaySpan();
        assertEquals(1L, span.getAttributes().get(OutboxRelay.CLAIMED), "claimed");
        assertEquals(0L, span.getAttributes().get(OutboxRelay.PUBLISHED), "published");
        assertFalse(span.getStatus().getStatusCode() == StatusCode.ERROR, "an unreachable broker is an expected condition");
    }

    @Test
    void anUnexpectedFailureMarksTheSpanAsErrorAndIsRethrown() {
        OutboxEventRepository broken = (OutboxEventRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{OutboxEventRepository.class}, (proxy, method, args) -> {
                    throw new IllegalStateException("database down");
                });
        OutboxRelay failing = new OutboxRelay(broken, publisher, clock, otel.getOpenTelemetry(), meters, "relay-b");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, failing::run);

        SpanData span = relaySpan();
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), "unexpected failures are errors");
        assertEquals(1, span.getEvents().size(), "the exception is recorded exactly once");
        assertEquals("database down", thrown.getMessage(), "the failure is not swallowed");
        failing.close();
    }

    // --- WARN ----------------------------------------------------------------------------------------------

    @Test
    void twoConsecutiveFailingRunsLogOneWarnWithoutPayload() {
        store(1);
        EventPublishException failure = new EventPublishException("broker down");
        publisher.failWith(failure);

        relay.run();
        clock.advance(OutboxRelay.LEASE.plusSeconds(1));
        relay.run();

        assertEquals(2, publisher.calls(), "precondition: the second run failed too");
        List<ILoggingEvent> warnings = warnings();
        assertEquals(1, warnings.size(), "WARN once per failure burst, got " + warnings);
        assertEquals(Level.WARN, warnings.getFirst().getLevel(), "level");
        assertSame(failure, ((ch.qos.logback.classic.spi.ThrowableProxy) warnings.getFirst().getThrowableProxy()).getThrowable(),
                "the exception is logged");
        assertFalse(warnings.getFirst().getFormattedMessage().contains(SECRET), "no payload in the message");
    }

    @Test
    void aFailureAfterARecoveryStartsANewBurstAndLogsAgain() {
        store(1);
        publisher.failWith(new EventPublishException("broker down"));
        relay.run();
        publisher.failWith(null);
        clock.advance(OutboxRelay.LEASE.plusSeconds(1));
        relay.run();
        store(1);
        publisher.failWith(new EventPublishException("broker down again"));
        clock.advance(Duration.ofSeconds(1));

        relay.run();

        assertEquals(2, warnings().size(), "one WARN per burst, and this is the second burst");
    }
}
