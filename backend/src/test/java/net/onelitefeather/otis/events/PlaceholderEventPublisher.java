package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * The {@link EventPublisher} bean of application contexts that enable events with
 * {@code otis.events.publisher=fake}: it publishes nothing, so the scheduler (kept idle by a long initial
 * delay) can never interfere. Tests that publish build their own relay with a {@link RecordingEventPublisher}.
 */
@Singleton
@Requires(property = "otis.events.publisher", value = "fake")
final class PlaceholderEventPublisher implements EventPublisher {

    @Override
    public void publish(String key, String payload) {
        throw new IllegalStateException("the placeholder publisher must never be used");
    }
}
