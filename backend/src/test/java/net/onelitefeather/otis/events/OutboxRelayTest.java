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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The relay against the real repository on H2 with a fake broker and a manual clock. The relay is driven by
 * calling its run method; the scheduler stays idle because of the long initial delay.
 */
@MicronautTest(environments = "test", transactional = false)
@Property(name = "otis.events.enabled", value = "true")
@Property(name = "otis.events.publisher", value = "fake")
@Property(name = "otis.events.relay.initial-delay", value = "1h")
class OutboxRelayTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    @Inject
    OutboxEventRepository outbox;

    @Inject
    DataSource dataSource;

    private final ManualClock clock = new ManualClock(START);
    private final RecordingEventPublisher publisher = new RecordingEventPublisher();
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        OutboxRows.clear(dataSource);
        relay = relay("relay-a");
    }

    private OutboxRelay relay(String instanceId) {
        return new OutboxRelay(outbox, publisher, clock, OpenTelemetry.noop(), new SimpleMeterRegistry(), instanceId);
    }

    private UUID store(UUID player, String marker, Instant createdAt) {
        UUID id = UUID.randomUUID();
        outbox.save(new OutboxEvent(id, player.toString(), "net.onelitefeather.otis.account.linked",
                "{\"marker\":\"" + marker + "\"}", createdAt));
        return id;
    }

    @Test
    void publishesInCreatedAtOrderWithThePlayerAsKeyAndMarksRowsPublished() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        // stored out of order on purpose: the relay must order by created_at, not by insertion
        store(bob, "second", START.plusSeconds(2));
        UUID first = store(alice, "first", START.plusSeconds(1));
        store(alice, "third", START.plusSeconds(3));

        OutboxRelay.Run run = relay.run();

        assertEquals(new OutboxRelay.Run(3, 3, false), run, "all three rows are claimed and published");
        List<RecordingEventPublisher.Message> sent = publisher.published();
        assertEquals(List.of(alice.toString(), bob.toString(), alice.toString()),
                sent.stream().map(RecordingEventPublisher.Message::key).toList(), "keys in created_at order");
        assertEquals(List.of("first", "second", "third"),
                sent.stream().map(m -> m.payload().replaceAll("\\D*(first|second|third).*", "$1")).toList(),
                "payloads in created_at order");
        assertEquals(0, OutboxRows.pending(dataSource), "published rows are marked");
        assertEquals(1, OutboxRows.attempts(dataSource, first), "the claim counts one attempt");
    }

    @Test
    void aPublishedRowIsNotPublishedAgain() {
        store(UUID.randomUUID(), "once", START);
        relay.run();

        OutboxRelay.Run second = relay.run();

        assertEquals(new OutboxRelay.Run(0, 0, false), second, "nothing is left to claim");
        assertEquals(1, publisher.published().size(), "the event was sent exactly once");
    }

    @Test
    void aFailingPublisherStopsTheRunAndTheNextRunRetriesAfterTheLeaseExpired() {
        UUID player = UUID.randomUUID();
        store(player, "first", START.plusSeconds(1));
        store(player, "second", START.plusSeconds(2));
        publisher.failWith(new EventPublishException("broker down"));

        OutboxRelay.Run failed = relay.run();

        assertEquals(new OutboxRelay.Run(2, 0, true), failed, "both rows claimed, none published");
        assertEquals(1, publisher.calls(), "the run stops at the first failure, so the second event is not sent ahead");
        assertEquals(2, OutboxRows.pending(dataSource), "failed rows stay stored");

        publisher.failWith(null);
        assertEquals(new OutboxRelay.Run(0, 0, false), relay.run(), "the lease of the failed run is still held");

        clock.advance(OutboxRelay.LEASE.plusSeconds(1));
        OutboxRelay.Run retried = relay.run();

        assertEquals(new OutboxRelay.Run(2, 2, false), retried, "after the lease expired both rows are retried");
        assertEquals(List.of("first", "second"),
                publisher.published().stream().map(m -> m.payload().replaceAll("\\D*(first|second).*", "$1")).toList(),
                "retry keeps the order");
    }

    @Test
    void aRowLeasedByAnotherReplicaIsSkippedUntilItsLeaseExpired() {
        UUID id = store(UUID.randomUUID(), "leased", START);
        // a replica that claimed the row and died before publishing
        assertEquals(1, outbox.claim(id, "dead-replica", START, START.plus(OutboxRelay.LEASE)), "setup: foreign claim");

        assertEquals(0, relay.run().claimed(), "a live lease keeps the row away from this replica");

        clock.advance(OutboxRelay.LEASE.plusSeconds(1));
        OutboxRelay.Run reclaimed = relay.run();

        assertEquals(new OutboxRelay.Run(1, 1, false), reclaimed, "an expired lease is reclaimed");
        assertEquals(2, OutboxRows.attempts(dataSource, id), "both claims are counted as attempts");
    }

    @Test
    void retentionDeletesOnlyRowsPublishedMoreThanSevenDaysAgo() {
        UUID old = store(UUID.randomUUID(), "old", START);
        UUID recent = store(UUID.randomUUID(), "recent", START);
        UUID unpublished = store(UUID.randomUUID(), "unpublished", START.minus(Duration.ofDays(30)));
        publishAt(old, START.minus(Duration.ofDays(8)));
        publishAt(recent, START.minus(Duration.ofDays(6)));

        int deleted = relay.deleteExpired();

        assertEquals(1, deleted, "exactly one row is older than the retention");
        assertFalse(OutboxRows.exists(dataSource, old), "published 8 days ago: deleted");
        assertTrue(OutboxRows.exists(dataSource, recent), "published 6 days ago: kept");
        assertTrue(OutboxRows.exists(dataSource, unpublished), "unpublished rows are never deleted, however old");
    }

    private void publishAt(UUID id, Instant at) {
        assertEquals(1, outbox.claim(id, "setup", at.minusSeconds(1), at.plusSeconds(60)), "setup: claim");
        assertEquals(1, outbox.markPublished(id, "setup", at), "setup: mark published");
    }
}
