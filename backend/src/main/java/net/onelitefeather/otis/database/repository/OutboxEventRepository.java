package net.onelitefeather.otis.database.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.GenericRepository;
import net.onelitefeather.otis.database.entity.OutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of {@link OutboxEvent}. Every claim is a conditional update on one row, which behaves the same on
 * PostgreSQL, MariaDB and H2 (no {@code SKIP LOCKED}, no subquery on the target table).
 */
@Repository
public interface OutboxEventRepository extends GenericRepository<OutboxEvent, UUID> {

    OutboxEvent save(OutboxEvent event);

    Optional<OutboxEvent> findById(UUID id);

    /** @return the ids of unpublished rows without a live lease at {@code now}, oldest first */
    @Query("SELECT event.id FROM OutboxEvent event WHERE event.publishedAt IS NULL "
            + "AND (event.claimedUntil IS NULL OR event.claimedUntil < :now) ORDER BY event.createdAt, event.id")
    List<UUID> findClaimableIds(Instant now, Pageable pageable);

    /**
     * Takes the lease on one row: only one caller can win while the row is unpublished and not leased.
     *
     * @return 1 when this caller now owns the row, 0 otherwise
     */
    @Query("UPDATE OutboxEvent event SET event.claimedBy = :instance, event.claimedUntil = :until, "
            + "event.attempts = event.attempts + 1 WHERE event.id = :id AND event.publishedAt IS NULL "
            + "AND (event.claimedUntil IS NULL OR event.claimedUntil < :now)")
    int claim(UUID id, String instance, Instant now, Instant until);

    /** @return 1 when the row was marked as published by the instance that holds its lease */
    @Query("UPDATE OutboxEvent event SET event.publishedAt = :now WHERE event.id = :id "
            + "AND event.claimedBy = :instance AND event.publishedAt IS NULL")
    int markPublished(UUID id, String instance, Instant now);

    @Query("SELECT COUNT(event) FROM OutboxEvent event WHERE event.publishedAt IS NULL")
    long countPending();

    /** @return the number of deleted rows */
    @Query("DELETE FROM OutboxEvent event WHERE event.publishedAt IS NOT NULL AND event.publishedAt < :before")
    int deletePublishedBefore(Instant before);
}
