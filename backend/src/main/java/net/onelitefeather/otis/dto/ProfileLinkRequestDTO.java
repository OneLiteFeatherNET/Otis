package net.onelitefeather.otis.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Sets a public, unverified link.
 *
 * @param value a handle of the provider or an https URL on the provider's own domain, at most 200 characters
 */
@Serdeable
@Introspected
public record ProfileLinkRequestDTO(
        @Schema(description = "A handle of the provider or an https URL on the provider's own domain; at most 200 characters.",
                example = "https://www.youtube.com/@onelitefeather", maxLength = 200)
        @NotBlank
        String value
) {
}
