package net.onelitefeather.otis.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Port of the account link service for recording link events. Implementations store the event in the
 * transaction that is running on the calling thread (see
 * {@link net.onelitefeather.otis.service.LinkTransactions}), so an event exists if and only if the change does.
 */
public interface OutboxWriter {

    /** A link was created, upgraded to verified or (unverified) set. */
    void linked(UUID playerUuid, String provider, String externalId, boolean verified, Instant occurredAt);

    /** An existing link was removed. */
    void unlinked(UUID playerUuid, String provider, String externalId, boolean verified, Instant occurredAt);
}
