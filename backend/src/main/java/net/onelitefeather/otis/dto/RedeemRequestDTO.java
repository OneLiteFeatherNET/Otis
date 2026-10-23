package net.onelitefeather.otis.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The request of the redeeming service: the code a player received plus the external account that presented it.
 *
 * @param code        the link code, case-insensitive, with or without the dash
 * @param provider    the provider the code was issued for
 * @param externalId  the provider's stable id of the external account, authenticated by the redeeming service
 * @param displayName optional human-readable account name
 */
@Serdeable
@Introspected
public record RedeemRequestDTO(
        @Schema(description = "The link code as received by the player; case-insensitive, the dash is optional.",
                example = "K7Q4-MZ2A")
        @NotBlank
        @Size(max = 32)
        String code,
        @Schema(description = "Provider the code was issued for: discord, twitch, youtube, x, tiktok or github.",
                example = "discord")
        @NotBlank
        @Size(max = 32)
        String provider,
        @Schema(description = "Stable id of the external account, as authenticated by the redeeming service.",
                example = "123456789012345678", maxLength = 64)
        @NotBlank
        @Size(max = 64)
        String externalId,
        @Schema(description = "Optional human-readable name of the external account.", example = "meinerlp",
                maxLength = 100, nullable = true)
        @Nullable
        @Size(max = 100)
        String displayName
) {
}
