package net.onelitefeather.otis.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * A one-time code that proves a player to an external service. Only the SHA-256 hash of the normalized code
 * is stored. Holds state only; the rules live in {@link net.onelitefeather.otis.service.AccountLinkService}.
 */
@Entity
@Table(
        name = "link_code",
        uniqueConstraints = @UniqueConstraint(name = "uq_link_code_hash", columnNames = "code_hash"),
        indexes = @Index(name = "idx_link_code_player_created", columnList = "player_id, created_at")
)
public class LinkCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "provider", nullable = false, length = 16)
    private String provider;

    @Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public LinkCode() {
    }

    public LinkCode(UUID playerId, String provider, String codeHash, Instant createdAt, Instant expiresAt) {
        this.playerId = playerId;
        this.provider = provider;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getProvider() {
        return provider;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
