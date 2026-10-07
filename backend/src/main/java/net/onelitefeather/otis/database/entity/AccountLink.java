package net.onelitefeather.otis.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * A link between a player and an external account. Verified links carry an {@code externalId} proven by a
 * link code; unverified links carry a public {@code value} (handle or URL) only. Holds state only; the rules
 * live in {@link net.onelitefeather.otis.service.AccountLinkService}.
 * <p>
 * {@code playerId} references {@link OtisPlayer#getUuid()} (the primary key, not the Mojang uuid); the foreign
 * key with {@code ON DELETE CASCADE} exists in the database migration only.
 */
@Entity
@Table(
        name = "account_link",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_account_link_player_provider", columnNames = {"player_id", "provider"}),
                @UniqueConstraint(name = "uq_account_link_provider_external", columnNames = {"provider", "external_id"})
        }
)
public class AccountLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "provider", nullable = false, length = 16)
    private String provider;

    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "link_value", length = 200)
    private String linkValue;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    public AccountLink() {
    }

    public AccountLink(UUID playerId, String provider, String externalId, String linkValue, String displayName,
                       boolean verified, Instant linkedAt) {
        this.playerId = playerId;
        this.provider = provider;
        this.externalId = externalId;
        this.linkValue = linkValue;
        this.displayName = displayName;
        this.verified = verified;
        this.linkedAt = linkedAt;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getLinkValue() {
        return linkValue;
    }

    public void setLinkValue(String linkValue) {
        this.linkValue = linkValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }
}
