package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.UUID;

/** Used while event publishing is disabled (the default): no outbox row is written. */
@Singleton
@Secondary
public final class NoopOutboxWriter implements OutboxWriter {

    @Override
    public void linked(UUID playerUuid, String provider, String externalId, boolean verified, Instant occurredAt) {
        // publishing disabled
    }

    @Override
    public void unlinked(UUID playerUuid, String provider, String externalId, boolean verified, Instant occurredAt) {
        // publishing disabled
    }
}
