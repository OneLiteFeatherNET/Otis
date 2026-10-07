package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks beans that exist only when events are enabled ({@code KAFKA_ENABLED=true}) and are published to the
 * real Kafka broker. micronaut-kafka has no global enabled switch, so each Kafka bean of Otis carries this.
 * Tests that enable events with {@code otis.events.publisher=fake} get none of them.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Requires(property = "otis.events.enabled", value = "true")
@Requires(property = "otis.events.publisher", value = "kafka", defaultValue = "kafka")
public @interface WhenKafkaPublishing {
}
