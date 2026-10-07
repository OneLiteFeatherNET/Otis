package net.onelitefeather.otis.events;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Property;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.concurrent.Future;

/**
 * Micronaut Kafka producer for {@link EventTopics#ACCOUNT_LINKS}. All brokers must acknowledge a message
 * ({@code acks=all}) and the producer is idempotent, so retries inside the producer never duplicate a message.
 * Timeouts are short and bounded so an unreachable broker fails a relay run quickly instead of blocking it;
 * the outbox row stays stored and is retried. Producer spans and the W3C {@code traceparent} header come from
 * {@code micronaut-tracing-opentelemetry-kafka}.
 */
@KafkaClient(
        id = "otis-events",
        acks = KafkaClient.Acknowledge.ALL,
        maxBlock = "5s",
        properties = {
                @Property(name = "enable.idempotence", value = "true"),
                @Property(name = "request.timeout.ms", value = "10000"),
                @Property(name = "delivery.timeout.ms", value = "15000")
        })
@WhenKafkaPublishing
public interface LinkEventProducer {

    /** @return completes when the broker acknowledged the message */
    @Topic(EventTopics.ACCOUNT_LINKS)
    Future<RecordMetadata> send(@KafkaKey String key, String payload);
}
