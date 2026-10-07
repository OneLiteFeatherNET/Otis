package net.onelitefeather.otis.events;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Sends events to Kafka through {@link LinkEventProducer} and waits for the broker's acknowledgement. */
@Singleton
@WhenKafkaPublishing
public final class KafkaEventPublisher implements EventPublisher {

    /** Upper bound of one send; the producer's own delivery timeout is shorter. */
    private static final long ACK_TIMEOUT_SECONDS = 20;

    private final LinkEventProducer producer;

    @Inject
    public KafkaEventPublisher(LinkEventProducer producer) {
        this.producer = producer;
    }

    @Override
    public void publish(String key, String payload) {
        try {
            producer.send(key, payload).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("interrupted while waiting for the broker", e);
        } catch (ExecutionException | TimeoutException e) {
            // the cause names the broker problem; neither key nor payload is part of any message
            throw new EventPublishException("the broker did not acknowledge the event", e);
        }
    }
}
