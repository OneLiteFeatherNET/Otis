package net.onelitefeather.otis.events;

import java.time.Instant;
import java.util.UUID;

/** Builds the account link events. Pure functions, no I/O, so the contract is unit testable. */
public final class LinkEvents {

    public static final String LINKED = "net.onelitefeather.otis.account.linked";
    public static final String UNLINKED = "net.onelitefeather.otis.account.unlinked";

    private LinkEvents() {
    }

    /** A link was created, upgraded to verified or (unverified) set. */
    public static CloudEvent linked(UUID eventId, UUID playerUuid, String provider, String externalId,
                                    boolean verified, Instant occurredAt) {
        return event(LINKED, eventId, playerUuid, provider, externalId, verified, occurredAt);
    }

    /** An existing link was removed; the facts are those of the removed link. */
    public static CloudEvent unlinked(UUID eventId, UUID playerUuid, String provider, String externalId,
                                      boolean verified, Instant occurredAt) {
        return event(UNLINKED, eventId, playerUuid, provider, externalId, verified, occurredAt);
    }

    private static CloudEvent event(String type, UUID eventId, UUID playerUuid, String provider, String externalId,
                                    boolean verified, Instant occurredAt) {
        String player = playerUuid.toString();
        String time = occurredAt.toString();
        return new CloudEvent(CloudEvent.SPEC_VERSION, eventId.toString(), CloudEvent.SOURCE, type, player, time,
                CloudEvent.CONTENT_TYPE, new LinkEventData(player, provider, verified ? externalId : null, verified, time));
    }
}
