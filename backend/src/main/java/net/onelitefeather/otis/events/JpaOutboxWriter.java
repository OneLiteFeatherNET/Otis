package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.onelitefeather.otis.database.entity.OutboxEvent;
import net.onelitefeather.otis.database.repository.OutboxEventRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes the event as an {@code outbox_event} row through the repository, so it joins the transaction of the
 * caller. The payload is built here, at write time; a later publish failure is therefore a broker problem and
 * never a data problem.
 */
@Singleton
@Requires(property = "otis.events.enabled", value = "true")
public final class JpaOutboxWriter implements OutboxWriter {

    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;

    @Inject
    public JpaOutboxWriter(OutboxEventRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Override
    public void linked(UUID playerUuid, String provider, String externalId, boolean verified, Instant occurredAt) {
        store(LinkEvents.linked(UUID.randomUUID(), playerUuid, provider, externalId, verified, occurredAt));
    }

    @Override
    public void unlinked(UUID playerUuid, String provider, String externalId, boolean verified, Instant occurredAt) {
        store(LinkEvents.unlinked(UUID.randomUUID(), playerUuid, provider, externalId, verified, occurredAt));
    }

    private void store(CloudEvent event) {
        String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot serialize the " + event.type() + " event", e);
        }
        outbox.save(new OutboxEvent(UUID.fromString(event.id()), event.subject(), event.type(), payload,
                Instant.parse(event.time())));
    }
}
