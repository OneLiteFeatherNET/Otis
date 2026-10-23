package net.onelitefeather.otis.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A link together with the player it belongs to; the answer of a redeem and of a lookup.
 *
 * @param playerUuid the Mojang uuid of the player
 * @param link       the link
 */
@Serdeable
@Introspected
public record LinkLookupDTO(
        @Schema(description = "Mojang uuid of the player the link belongs to.", type = "string", format = "uuid",
                example = "123e4567-e89b-12d3-a456-426614174001")
        UUID playerUuid,
        @Schema(description = "The link.")
        AccountLinkDTO link
) {
}
