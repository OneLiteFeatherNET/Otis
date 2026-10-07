package net.onelitefeather.otis.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A freshly issued link code. The code is shown once; Otis keeps only its hash.
 *
 * @param code      the one-time code, {@code XXXX-XXXX}
 * @param provider  the provider the code was issued for
 * @param expiresAt when the code stops being redeemable
 */
@Serdeable
@Introspected
public record LinkCodeDTO(
        @Schema(description = "The one-time link code: two groups of four Crockford Base32 characters. Shown only once.",
                example = "K7Q4-MZ2A", pattern = "^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$")
        String code,
        @Schema(description = "Provider the code was issued for.", example = "discord")
        String provider,
        @Schema(description = "Instant after which the code can no longer be redeemed (10 minutes after issue).",
                type = "string", format = "date-time", example = "2026-01-01T12:10:00Z")
        Instant expiresAt
) {
}
