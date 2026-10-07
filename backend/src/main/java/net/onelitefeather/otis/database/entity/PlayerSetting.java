package net.onelitefeather.otis.database.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import net.kyori.adventure.key.Key;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A single setting of a player. Holds state only; the rules live in
 * {@link net.onelitefeather.otis.service.PlayerSettingService}.
 * <p>
 * {@code playerId} references {@link OtisPlayer#getUuid()} (the primary key, not the Mojang uuid); the
 * foreign key with {@code ON DELETE CASCADE} exists in the database migration only.
 * {@code version} is a plain counter managed by the service, deliberately not a JPA {@code @Version}.
 */
@Entity
@Table(
        name = "player_setting",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_player_setting_key", columnNames = {"player_id", "key_namespace", "key_value"}),
        indexes = @Index(name = "idx_player_setting_namespace", columnList = "player_id, key_namespace")
)
public class PlayerSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "key_namespace", nullable = false)
    private String keyNamespace;

    @Column(name = "key_value", nullable = false)
    private String keyValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "setting_value", nullable = false)
    private JsonNode value;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlayerSetting() {
    }

    public PlayerSetting(UUID playerId, Key key, JsonNode value, long version, Instant updatedAt) {
        this.playerId = playerId;
        this.keyNamespace = key.namespace();
        this.keyValue = key.value();
        this.value = value;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getKeyNamespace() {
        return keyNamespace;
    }

    public String getKeyValue() {
        return keyValue;
    }

    /** @return the key built from namespace and value column */
    public Key key() {
        return Key.key(keyNamespace, keyValue);
    }

    public JsonNode getValue() {
        return value;
    }

    public void setValue(JsonNode value) {
        this.value = value;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
