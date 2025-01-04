package net.theevilreaper.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

@Serdeable
@Introspected
public record OtisPlayerDTO(
        UUID uuid,
        @Size(min = 3, max = 16, message = "Username must be between 3 and 16 characters.")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain alphanumeric characters and underscores.")
        String playerName,
        long firstJoin,
        long lastJoin,
        Map<String, Object> profileTextures
) {
}
