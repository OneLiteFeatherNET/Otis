package net.onelitefeather.otis.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import net.onelitefeather.otis.database.entity.AccountLink;

import java.time.Instant;

/**
 * A link of a player to an external account as exposed by the API.
 *
 * @param provider    lowercase provider name
 * @param externalId  the verified external account id; absent for unverified links
 * @param value       the public handle or URL of an unverified link; absent for verified links
 * @param displayName optional human-readable account name of a verified link
 * @param verified    whether the link was proven with a link code
 * @param linkedAt    when the link was created or last replaced
 */
@Serdeable
@Introspected
public record AccountLinkDTO(
        @Schema(description = "Provider of the link.", example = "discord")
        String provider,
        @Schema(description = "Id of the external account; only set for verified links.", example = "123456789012345678",
                nullable = true)
        @Nullable String externalId,
        @Schema(description = "Public handle or https URL of an unverified link; only set for unverified links.",
                example = "https://www.youtube.com/@onelitefeather", nullable = true)
        @Nullable String value,
        @Schema(description = "Human-readable account name of a verified link.", example = "meinerlp", nullable = true)
        @Nullable String displayName,
        @Schema(description = "True if the link was proven with a link code, false for a public claim.", example = "true")
        boolean verified,
        @Schema(description = "Instant the link was created or last replaced.",
                type = "string", format = "date-time", example = "2026-01-01T12:00:00Z")
        Instant linkedAt
) {

    /**
     * @param link the stored link
     * @return the DTO for {@code link}
     */
    public static AccountLinkDTO of(AccountLink link) {
        return new AccountLinkDTO(link.getProvider(), link.getExternalId(), link.getLinkValue(), link.getDisplayName(),
                link.isVerified(), link.getLinkedAt());
    }
}
