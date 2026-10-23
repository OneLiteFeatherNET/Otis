package net.onelitefeather.otis.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** A fake broker for tests: records what is published and can be told to fail. Thread safe. */
public final class RecordingEventPublisher implements EventPublisher {

    /** One published message. */
    public record Message(String key, String payload) {
    }

    private final List<Message> published = new CopyOnWriteArrayList<>();
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public void publish(String key, String payload) {
        calls.incrementAndGet();
        RuntimeException toThrow = failure.get();
        if (toThrow != null) {
            throw toThrow;
        }
        published.add(new Message(key, payload));
    }

    /** @return how often publish was called, successful or not */
    public int calls() {
        return calls.get();
    }

    public List<Message> published() {
        return List.copyOf(published);
    }

    /** From now on every publish fails with {@code failure}; {@code null} makes the broker healthy again. */
    public void failWith(RuntimeException failure) {
        this.failure.set(failure);
    }
}
