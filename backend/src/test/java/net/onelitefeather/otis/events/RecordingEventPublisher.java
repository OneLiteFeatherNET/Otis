package net.onelitefeather.otis.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** A fake broker for tests: records what is published and can be told to fail. Thread safe. */
public final class RecordingEventPublisher implements EventPublisher {

    /** One published message. */
    public record Message(String key, String payload) {
    }

    private final List<Message> published = new CopyOnWriteArrayList<>();
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    @Override
    public void publish(String key, String payload) {
        RuntimeException toThrow = failure.get();
        if (toThrow != null) {
            throw toThrow;
        }
        published.add(new Message(key, payload));
    }

    public List<Message> published() {
        return List.copyOf(published);
    }

    /** From now on every publish fails with {@code failure}; {@code null} makes the broker healthy again. */
    public void failWith(RuntimeException failure) {
        this.failure.set(failure);
    }
}
