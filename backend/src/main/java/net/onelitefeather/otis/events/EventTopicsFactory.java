package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.admin.NewTopic;

/**
 * Declares the topic Otis publishes to. micronaut-kafka creates every declared {@link NewTopic} bean through
 * its {@code AdminClient} at startup ({@code KafkaNewTopics}); a topic that already exists is left unchanged.
 * The cluster has {@code auto.create.topics.enable=false} and no topic operator, so Otis creates its own topic.
 */
@Factory
@WhenKafkaPublishing
class EventTopicsFactory {

    @Bean
    @Singleton
    NewTopic accountLinksTopic() {
        return new NewTopic(EventTopics.ACCOUNT_LINKS, EventTopics.PARTITIONS, EventTopics.REPLICATION_FACTOR);
    }
}
