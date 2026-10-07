package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.data.model.Pageable;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Scheduled;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.onelitefeather.otis.database.entity.OutboxEvent;
import net.onelitefeather.otis.database.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes stored outbox events to the message broker, at least once and safe with several replicas.
 * <p>
 * Each {@linkplain #run() run} selects the oldest unpublished rows, takes a lease on each with a conditional
 * update (only one replica wins a row), sends the won rows in {@code created_at} order and marks them published.
 * At the first failure the run stops: the leases expire and a later run retries in order, so the events of one
 * player (same key) are never sent ahead of an earlier unpublished one. A crash between send and marking
 * causes a duplicate with the same event id; consumers deduplicate by it.
 * <p>
 * The scheduler is only a trigger: tests call {@link #run()} and {@link #deleteExpired()} directly.
 * Payloads are never logged.
 */
@Singleton
@Requires(property = "otis.events.enabled", value = "true")
public class OutboxRelay {

    /** How long a replica owns a claimed row before others may take it. */
    public static final Duration LEASE = Duration.ofSeconds(30);
    /** Rows claimed per run. */
    public static final int BATCH_SIZE = 100;
    /** Age of a published row after which it is deleted. */
    public static final Duration RETENTION = Duration.ofDays(7);

    static final String INSTRUMENTATION_SCOPE = "net.onelitefeather.otis";
    static final String SPAN_NAME = "outbox.relay";
    static final String PENDING_METRIC = "otis.outbox.pending";
    static final AttributeKey<Long> CLAIMED = AttributeKey.longKey("otis.outbox.claimed");
    static final AttributeKey<Long> PUBLISHED = AttributeKey.longKey("otis.outbox.published");

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

    /** The outcome of one run. */
    public record Run(int claimed, int published, boolean failed) {
    }

    private final OutboxEventRepository outbox;
    private final EventPublisher publisher;
    private final Clock clock;
    private final String instanceId;
    private final Tracer tracer;
    private final ObservableLongGauge pendingGauge;
    /** Cached by each run so the gauge callback never touches the database. */
    private final AtomicLong pending = new AtomicLong();
    /** True after a failed run until a later run publishes again; makes the WARN once per failure burst. */
    private final AtomicBoolean failing = new AtomicBoolean();

    @Inject
    public OutboxRelay(OutboxEventRepository outbox, EventPublisher publisher, Clock clock,
                       OpenTelemetry openTelemetry, @Value("${otis.events.instance-id:}") String configuredInstanceId) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.instanceId = configuredInstanceId == null || configuredInstanceId.isBlank()
                ? UUID.randomUUID().toString()
                : configuredInstanceId;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        this.pendingGauge = openTelemetry.getMeter(INSTRUMENTATION_SCOPE).gaugeBuilder(PENDING_METRIC)
                .ofLongs()
                .setUnit("{event}")
                .setDescription("Outbox events not yet published to Kafka")
                .buildWithCallback(measurement -> measurement.record(pending.get()));
    }

    @EventListener
    void onStartup(StartupEvent event) {
        LOGGER.atInfo().addKeyValue("topic", EventTopics.ACCOUNT_LINKS).addKeyValue("instance", instanceId)
                .log("outbox relay started");
    }

    @PreDestroy
    void close() {
        pendingGauge.close();
    }

    /** Trigger of {@link #run()}. */
    @Scheduled(fixedDelay = "${otis.events.relay.interval:2s}", initialDelay = "${otis.events.relay.initial-delay:10s}")
    void scheduledRun() {
        run();
    }

    /** Trigger of {@link #deleteExpired()}, hourly at minute 15. */
    @Scheduled(cron = "0 15 * * * *")
    void scheduledRetention() {
        deleteExpired();
    }

    /** Claims, publishes and marks one batch; see the class comment. */
    public Run run() {
        Span span = tracer.spanBuilder(SPAN_NAME).setSpanKind(SpanKind.INTERNAL).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            List<OutboxEvent> claimed = claim();
            int published = 0;
            boolean failed = false;
            for (OutboxEvent event : claimed) {
                try {
                    publisher.publish(event.getAggregateKey(), event.getPayload());
                } catch (RuntimeException e) {
                    failed = true;
                    warnOncePerBurst(e, claimed.size() - published);
                    break;
                }
                outbox.markPublished(event.getId(), instanceId, now());
                published++;
            }
            if (!failed && published > 0) {
                failing.set(false);
            }
            pending.set(outbox.countPending());
            span.setAttribute(CLAIMED, (long) claimed.size());
            span.setAttribute(PUBLISHED, (long) published);
            LOGGER.atDebug().addKeyValue("claimed", claimed.size()).addKeyValue("published", published)
                    .addKeyValue("pending", pending.get()).log("outbox relay run finished");
            return new Run(claimed.size(), published, failed);
        } catch (RuntimeException unexpected) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(unexpected);
            throw unexpected;
        } finally {
            span.end();
        }
    }

    /** Deletes the rows that were published more than {@link #RETENTION} ago. */
    public int deleteExpired() {
        int deleted = outbox.deletePublishedBefore(now().minus(RETENTION));
        LOGGER.atDebug().addKeyValue("deleted", deleted).log("outbox retention finished");
        return deleted;
    }

    private List<OutboxEvent> claim() {
        Instant now = now();
        Instant until = now.plus(LEASE);
        List<OutboxEvent> claimed = new ArrayList<>();
        for (UUID id : outbox.findClaimableIds(now, Pageable.from(0, BATCH_SIZE))) {
            if (outbox.claim(id, instanceId, now, until) == 1) {
                outbox.findById(id).ifPresent(claimed::add);
            }
        }
        claimed.sort(Comparator.comparing(OutboxEvent::getCreatedAt).thenComparing(OutboxEvent::getId));
        return claimed;
    }

    private void warnOncePerBurst(RuntimeException failure, int unsent) {
        if (!failing.getAndSet(true)) {
            LOGGER.atWarn().setCause(failure).addKeyValue("unsent", unsent)
                    .log("publishing outbox events failed, they stay stored and are retried");
        }
    }

    /** Microsecond precision, like the stored timestamps. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
