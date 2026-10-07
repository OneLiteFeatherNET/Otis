package net.onelitefeather.otis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.database.entity.PlayerSetting;

import java.time.Instant;

/**
 * A stored setting as exposed by the API.
 *
 * @param key       the Adventure key, {@code namespace:value} on the wire
 * @param value     any JSON value, returned as stored (object, array, string, number, boolean or null)
 * @param version   starts at 1 and grows with every change of the value
 * @param updatedAt when the value last changed
 */
@Serdeable
@Introspected
public record PlayerSettingDTO(
        @Schema(description = "Setting key in Adventure key syntax; the namespace separates generic settings (olf) "
                + "from game-specific ones.",
                type = "string", format = "adventure-key", example = "lobby:player_hider")
        Key key,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "The setting value: any JSON value, stored and returned without interpretation.",
                example = "{\"enabled\":true}")
        Object value,
        @Schema(description = "Version of the setting, starting at 1 and increased with every change of the value.",
                example = "1", minimum = "1")
        long version,
        @Schema(description = "Instant of the last change of the value.",
                type = "string", format = "date-time", example = "2026-01-01T12:00:00Z")
        Instant updatedAt
) {

    /**
     * @param setting the stored setting
     * @param value   its value as plain Java objects (maps, lists, strings, numbers, booleans, null)
     * @return the DTO for {@code setting}
     */
    public static PlayerSettingDTO of(PlayerSetting setting, Object value) {
        return new PlayerSettingDTO(setting.key(), value, setting.getVersion(), setting.getUpdatedAt());
    }
}
