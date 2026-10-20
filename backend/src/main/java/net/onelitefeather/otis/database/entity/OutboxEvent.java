package net.onelitefeather.otis.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored event waiting to be published to Kafka (transactional outbox). The id is the CloudEvents {@code id}
 * and the aggregate key is the Kafka message key. Holds state only; the claim and publish rules live in
 * {@link net.onelitefeather.otis.events.OutboxRelay}.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_key", nullable = false, length = 36)
    private String aggregateKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /** The serialized CloudEvent; a JSON column ({@code jsonb} on PostgreSQL), written and read as text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "claimed_by", length = 64)
    private String claimedBy;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    public OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateKey, String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateKey = aggregateKey;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getClaimedUntil() {
        return claimedUntil;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }
}
