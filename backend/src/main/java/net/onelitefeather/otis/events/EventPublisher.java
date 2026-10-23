package net.onelitefeather.otis.events;

/**
 * Port of the {@link OutboxRelay}: sends one stored event to the message broker. The Kafka implementation is
 * {@link KafkaEventPublisher}; tests use a recording fake, so no broker is needed.
 */
public interface EventPublisher {

    /**
     * Sends one message and waits for the broker's acknowledgement.
     *
     * @param key     the message key (the player uuid); messages with the same key keep their order
     * @param payload the serialized CloudEvent
     * @throws EventPublishException if the broker did not acknowledge the message
     */
    void publish(String key, String payload) throws EventPublishException;
}
