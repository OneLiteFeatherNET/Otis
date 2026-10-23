package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.OpenTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OutboxEvent;
import net.onelitefeather.otis.database.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spec "Two replicas do not double-claim": two relay instances against the same H2 database. */
@MicronautTest(environments = "test", transactional = false)
@Property(name = "otis.events.enabled", value = "true")
@Property(name = "otis.events.publisher", value = "fake")
@Property(name = "otis.events.relay.initial-delay", value = "1h")
class OutboxRelayConcurrencyTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final int ROWS = 10;

    @Inject
    OutboxEventRepository outbox;

    @Inject
    DataSource dataSource;

    private final RecordingEventPublisher publisher = new RecordingEventPublisher();

    @BeforeEach
    void setUp() {
        OutboxRows.clear(dataSource);
    }

    private OutboxRelay relay(String instanceId) {
        return new OutboxRelay(outbox, publisher, new ManualClock(START), OpenTelemetry.noop(), new SimpleMeterRegistry(), instanceId);
    }

    @Test
    void everyRowIsPublishedExactlyOnceWhenTwoInstancesRunAtTheSameTime() throws Exception {
        IntStream.range(0, ROWS).forEach(i -> outbox.save(new OutboxEvent(UUID.randomUUID(),
                UUID.randomUUID().toString(), "net.onelitefeather.otis.account.linked", "{\"row\":" + i + "}",
                START.plusMillis(i))));
        OutboxRelay first = relay("relay-a");
        OutboxRelay second = relay("relay-b");
        CyclicBarrier startTogether = new CyclicBarrier(2);

        List<OutboxRelay.Run> runs;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<OutboxRelay.Run> a = executor.submit(() -> {
                startTogether.await();
                return first.run();
            });
            Future<OutboxRelay.Run> b = executor.submit(() -> {
                startTogether.await();
                return second.run();
            });
            runs = List.of(join(a), join(b));
        }

        Set<String> payloads = new HashSet<>();
        publisher.published().forEach(message -> payloads.add(message.payload()));
        assertEquals(ROWS, publisher.published().size(), "no row is published twice: " + publisher.published());
        assertEquals(ROWS, payloads.size(), "all ten distinct rows are published");
        assertEquals(ROWS, runs.stream().mapToInt(OutboxRelay.Run::claimed).sum(),
                "each row is claimed by exactly one instance: " + runs);
        assertEquals(0, OutboxRows.pending(dataSource), "nothing stays pending");
    }

    private static <T> T join(Future<T> future) throws InterruptedException, ExecutionException {
        return future.get();
    }
}
