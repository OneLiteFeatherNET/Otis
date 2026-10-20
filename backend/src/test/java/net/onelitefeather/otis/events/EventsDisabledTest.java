package net.onelitefeather.otis.events;

import io.micronaut.context.ApplicationContext;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import net.onelitefeather.otis.dto.LinkCodeDTO;
import net.onelitefeather.otis.dto.RedeemRequestDTO;
import net.onelitefeather.otis.service.AccountLinkService;
import io.micronaut.configuration.kafka.admin.KafkaNewTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Spec "Disabled by default": without {@code KAFKA_ENABLED} no Kafka bean exists and no outbox row is written. */
@MicronautTest(environments = "test", transactional = false)
class EventsDisabledTest {

    @Inject
    ApplicationContext context;

    @Inject
    AccountLinkService service;

    @Inject
    OtisPlayerRepository players;

    @Inject
    OutboxWriter writer;

    @Inject
    DataSource dataSource;

    @Test
    void noKafkaRelayOrPublisherBeansExist() {
        assertFalse(context.containsBean(KafkaNewTopics.class), "no topic creation at startup, so no admin client is created");
        assertFalse(context.containsBean(NewTopic.class), "no topic is declared");
        assertFalse(context.containsBean(LinkEventProducer.class), "no Kafka producer");
        assertFalse(context.containsBean(KafkaEventPublisher.class), "no Kafka publisher");
        assertFalse(context.containsBean(EventPublisher.class), "no publisher at all");
        assertFalse(context.containsBean(OutboxRelay.class), "no relay");
        assertFalse(context.containsBean(JpaOutboxWriter.class), "no real outbox writer");
    }

    @Test
    void theKafkaHealthIndicatorDoesNotProbeAnAbsentBroker() {
        List<String> kafkaIndicators = context.getBeansOfType(HealthIndicator.class).stream()
                .map(indicator -> indicator.getClass().getName()).filter(name -> name.contains("Kafka")).toList();

        assertEquals(List.of(), kafkaIndicators, "a Kafka health check would turn /health DOWN");
    }

    @Test
    void theOutboxWriterIsTheNoopWriter() {
        assertInstanceOf(NoopOutboxWriter.class, writer, "the disabled configuration uses the noop writer");
    }

    @Test
    void linkingWritesNoOutboxRow() {
        UUID mojangUuid = UUID.randomUUID();
        OtisPlayer saved = players.save(new OtisPlayer(null, mojangUuid,
                "D" + mojangUuid.toString().substring(0, 8).replace('-', '_'), 1000L, 2000L, Map.of(), Locale.US));
        try {
            LinkCodeDTO code = service.issueCode(mojangUuid, "discord");
            service.redeem(new RedeemRequestDTO(code.code(), "discord", "123456789012345678", "name"));
            service.putUnverified(mojangUuid, "github", "someone");
            service.delete(mojangUuid, "discord");

            assertEquals(List.of(), OutboxRows.of(dataSource, mojangUuid), "no event row while publishing is disabled");
        } finally {
            players.deleteById(saved.getUuid());
        }
    }
}
