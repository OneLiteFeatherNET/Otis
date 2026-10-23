package net.onelitefeather.otis.events;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.instrument.kafka.KafkaTelemetryProducerTracingInstrumentation;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec "Enabled" and "Topic created once", as far as it is testable without a broker: with {@code KAFKA_ENABLED}
 * on, the Kafka beans exist and are configured. The real broker path (topic creation, acknowledgement,
 * {@code traceparent} header) is verified after deployment.
 */
@MicronautTest(environments = "test", transactional = false)
@Property(name = "otis.events.enabled", value = "true")
@Property(name = "otis.events.relay.initial-delay", value = "1h")
// nothing listens here; client creation does not connect, so the context starts without a broker
@Property(name = "kafka.bootstrap.servers", value = "localhost:1")
class KafkaPublishingEnabledTest {

    @Inject
    ApplicationContext context;

    @Inject
    EventPublisher publisher;

    @Inject
    OutboxWriter writer;

    @Test
    void theKafkaPublisherRelayAndRealWriterExist() {
        assertInstanceOf(KafkaEventPublisher.class, publisher, "events go to Kafka");
        assertInstanceOf(JpaOutboxWriter.class, writer, "link changes are stored in the outbox");
        assertTrue(context.containsBean(OutboxRelay.class), "the relay exists");
    }

    @Test
    void theTopicIsDeclaredWithThreePartitionsAndReplicationFactorThree() {
        NewTopic topic = context.getBean(NewTopic.class);

        assertEquals("otis.account-links", topic.name(), "topic name");
        assertEquals(3, topic.numPartitions(), "partitions");
        assertEquals((short) 3, topic.replicationFactor(), "replication factor");
    }

    @Test
    void theProducerWaitsForAllBrokersAndIsIdempotent() {
        BeanDefinition<LinkEventProducer> definition = context.getBeanDefinition(LinkEventProducer.class);

        assertEquals(KafkaClient.Acknowledge.ALL, definition.intValue(KafkaClient.class, "acks").orElseThrow(),
                "acks=all");
        List<String> properties = Arrays.stream(definition.getAnnotation(KafkaClient.class)
                .getAnnotations("properties", io.micronaut.context.annotation.Property.class).toArray(
                        io.micronaut.core.annotation.AnnotationValue[]::new))
                .map(value -> value.stringValue("name").orElse("") + "=" + value.stringValue("value").orElse(""))
                .toList();
        assertTrue(properties.contains("enable.idempotence=true"), "idempotent producer: " + properties);
    }

    @Test
    void producerSpansAndTraceparentHeadersAreWiredIn() {
        assertTrue(context.containsBean(KafkaTelemetryProducerTracingInstrumentation.class),
                "micronaut-tracing-opentelemetry-kafka instruments the producer");
    }
}
