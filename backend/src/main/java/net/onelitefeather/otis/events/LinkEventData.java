package net.onelitefeather.otis.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The {@code data} of an account link event: identifiers and facts only. Never a link code, its hash, a display
 * name, the value of an unverified link or any provider token.
 *
 * @param playerUuid the Mojang uuid of the player
 * @param provider   the wire name of the provider, e.g. {@code discord}
 * @param externalId the external account id; {@code null} for unverified links (serialized as JSON null)
 * @param verified   whether the link is proven by a link code
 * @param occurredAt when the change happened, ISO-8601 in UTC
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LinkEventData(String playerUuid, String provider, String externalId, boolean verified,
                            String occurredAt) {
}
