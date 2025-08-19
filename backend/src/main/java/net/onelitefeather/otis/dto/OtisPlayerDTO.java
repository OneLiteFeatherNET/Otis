package net.onelitefeather.otis.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Serdeable
@Introspected
public record OtisPlayerDTO(
        @Schema(description = "Unique identifier for the player in the Otis system.",
                example = "123e4567-e89b-12d3-a456-426614174000", type = "string", format = "uuid")
        UUID uuid,
        @Schema(description = "Unique identifier for the player from mojang.",
                example = "123e4567-e89b-12d3-a456-426614174001", type = "string", format = "uuid"
        )
        UUID playerUuid,
        @Size(min = 3, max = 16, message = "Username must be between 3 and 16 characters.")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain alphanumeric characters and underscores.")
        @Schema(description = "The player's username.",
                example = "Player123", type = "string", format = "username",
                minimum = "3", maximum = "16", pattern = "^[a-zA-Z0-9_]+$"
        )
        String playerName,
        @Schema(description = "The timestamp of the player's first join in milliseconds since epoch.",
                example = "1633072800000", type = "integer", format = "int64"
        )
        long firstJoin,
        @Schema(description = "The timestamp of the player's last join in milliseconds since epoch.",
                example = "1633072800000", type = "integer", format = "int64"
        )
        long lastJoin,
        @Schema(description = "A map containing the player's profile textures, such as skin and cape.")
        Map<String, Object> profileTextures,
        @Schema(description = "The locale of the player, used for localization purposes.",
                example = "en-US", type = "string", format = "locale"
        )
        Locale locale
) {
}
