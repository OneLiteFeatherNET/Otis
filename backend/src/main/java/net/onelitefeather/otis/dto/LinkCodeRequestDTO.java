package net.onelitefeather.otis.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requests a link code.
 *
 * @param provider the provider to link: discord, twitch, youtube, x, tiktok or github
 */
@Serdeable
@Introspected
public record LinkCodeRequestDTO(
        @Schema(description = "Provider to link: discord, twitch, youtube, x, tiktok or github.", example = "discord")
        @NotBlank
        @Size(max = 32)
        String provider
) {
}
