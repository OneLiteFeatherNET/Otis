package net.theevilreaper.otis.database.entity;

import io.micronaut.serde.annotation.Serdeable;
import net.theevilreaper.otis.database.converter.LocaleAttributeConverter;
import net.theevilreaper.otis.database.converter.MapStringObjectConverter;
import net.theevilreaper.otis.dto.OtisPlayerDTO;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.Locale;
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

    @ColumnDefault(value = "en-US")
    @Convert(converter = LocaleAttributeConverter.class)
    private Locale locale;

    public static OtisPlayer toEntity(OtisPlayerDTO otisPlayerDTO) {
        return new OtisPlayer(
                otisPlayerDTO.uuid(),
                otisPlayerDTO.playerName(),
                otisPlayerDTO.firstJoin(),
                otisPlayerDTO.lastJoin(),
                otisPlayerDTO.profileTextures(),
                otisPlayerDTO.locale()
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
     * @param locale         the locale from the player
     */
    public OtisPlayer(UUID uuid, String playerName, long firstJoin, long lastJoin, Map<String, Object> profileTexture, Locale locale) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.profileTexture = profileTexture;
        this.locale = locale;
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

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public Map<String, Object> getProfileTexture() {
        return profileTexture;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Locale getLocale() {
        return locale;
    }

    public OtisPlayerDTO toDto() {
        return new OtisPlayerDTO(
                this.uuid,
                this.playerName,
                this.firstJoin,
                this.lastJoin,
                this.profileTexture,
                this.locale
        );
    }
}
