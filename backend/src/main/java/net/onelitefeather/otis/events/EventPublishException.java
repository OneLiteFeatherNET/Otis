package net.onelitefeather.otis.events;

/** The broker did not acknowledge a message; the event stays in the outbox and is retried. */
public final class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventPublishException(String message) {
        super(message);
    }
}
