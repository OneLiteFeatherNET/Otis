package net.theevilreaper.database.entity;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import net.theevilreaper.database.converter.MapStringObjectConverter;
import net.theevilreaper.dto.OtisPlayerDTO;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Serdeable
@Entity
@Table(name = "otis_player", indexes = @Index(columnList = "uuid"))
public class OtisPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    private String playerName;

    private long firstJoin;
    private long lastJoin;

    @Convert(converter = MapStringObjectConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> profileTexture;

    public static OtisPlayer toEntity(OtisPlayerDTO otisPlayerDTO) {
        return new OtisPlayer(
                otisPlayerDTO.uuid(),
                otisPlayerDTO.playerName(),
                otisPlayerDTO.firstJoin(),
                otisPlayerDTO.lastJoin(),
                otisPlayerDTO.profileTextures()
        );
    }

    public static OtisPlayer toEntity(Optional<OtisPlayerDTO> otisPlayerDTO) {
        return otisPlayerDTO.map(OtisPlayer::toEntity).orElse(null);
    }

    public OtisPlayer() {
    }

    /**
     * Creates a new instance of the {@link OtisPlayer} with all required values.
     *
     * @param uuid           the unique identifier from the player
     * @param playerName     the name from the player
     * @param firstJoin      the first join from the player
     * @param lastJoin       the last join from the player
     * @param profileTexture the profile texture from the player
     */
    public OtisPlayer(UUID uuid, String playerName, long firstJoin, long lastJoin, Map<String, Object> profileTexture) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.profileTexture = profileTexture;
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public long getLastJoin() {
        return lastJoin;
    }

    public void setLastJoin(long lastJoin) {
        this.lastJoin = lastJoin;
    }

    public void setProfileTexture(Map<String, Object> profileTexture) {
        this.profileTexture = profileTexture;
    }

    public Map<String, Object> getProfileTexture() {
        return profileTexture;
    }

    public String getPlayerName() {
        return playerName;
    }

    public OtisPlayerDTO toDto() {
        return new OtisPlayerDTO(uuid, playerName, firstJoin, lastJoin, profileTexture);
    }
}
