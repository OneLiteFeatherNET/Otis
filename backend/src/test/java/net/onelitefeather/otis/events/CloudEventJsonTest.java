package net.onelitefeather.otis.events;

import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JSON contract of the account link events (spec: Event format), no database and no Kafka. */
class CloudEventJsonTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PLAYER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final String EXTERNAL_ID = "123456789012345678";

    private final ObjectMapper mapper = ObjectMapper.create(Map.of());

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(CloudEvent event) throws IOException {
        return mapper.readValue(mapper.writeValueAsString(event), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> json) {
        return (Map<String, Object>) json.get("data");
    }

    @Test
    void linkedEventHasTheCloudEventsAttributes() throws IOException {
        Map<String, Object> json = json(LinkEvents.linked(EVENT_ID, PLAYER, "discord", EXTERNAL_ID, true, AT));

        assertEquals("1.0", json.get("specversion"), "specversion");
        assertEquals(EVENT_ID.toString(), json.get("id"), "id");
        assertEquals("/otis", json.get("source"), "source");
        assertEquals("net.onelitefeather.otis.account.linked", json.get("type"), "type");
        assertEquals(PLAYER.toString(), json.get("subject"), "subject is the player uuid");
        assertEquals("2026-01-01T12:00:00Z", json.get("time"), "time");
        assertEquals("application/json", json.get("datacontenttype"), "datacontenttype");
        assertEquals(8, json.size(), "exactly the eight CloudEvents attributes: " + json.keySet());
    }

    @Test
    void linkedEventDataCarriesOnlyIdentifiersAndFacts() throws IOException {
        Map<String, Object> data = data(json(LinkEvents.linked(EVENT_ID, PLAYER, "discord", EXTERNAL_ID, true, AT)));

        assertEquals(Map.of("playerUuid", PLAYER.toString(), "provider", "discord", "externalId", EXTERNAL_ID,
                "verified", true, "occurredAt", "2026-01-01T12:00:00Z"), data, "data of a verified link");
    }

    @Test
    void unverifiedLinkHasANullExternalIdThatIsStillPresent() throws IOException {
        Map<String, Object> data = data(json(LinkEvents.linked(EVENT_ID, PLAYER, "github", "ignored", false, AT)));

        assertTrue(data.containsKey("externalId"), "externalId is present as explicit null");
        assertNull(data.get("externalId"), "an unverified link has no externalId");
        assertEquals(false, data.get("verified"), "verified");
    }

    @Test
    void unlinkedEventHasItsOwnTypeAndTheFactsOfTheRemovedLink() throws IOException {
        Map<String, Object> json = json(LinkEvents.unlinked(EVENT_ID, PLAYER, "discord", EXTERNAL_ID, true, AT));

        assertEquals("net.onelitefeather.otis.account.unlinked", json.get("type"), "type");
        assertEquals(EXTERNAL_ID, data(json).get("externalId"), "externalId of the removed verified link");
    }

    @Test
    void eventsNeverCarryCodesDisplayNamesOrLinkValues() throws IOException {
        String text = mapper.writeValueAsString(LinkEvents.linked(EVENT_ID, PLAYER, "twitch", null, false, AT));

        assertFalse(text.contains("code"), "no code field: " + text);
        assertFalse(text.contains("displayName"), "no display name field: " + text);
        assertFalse(text.contains("value"), "no link value field: " + text);
        assertFalse(text.contains("token"), "no token field: " + text);
    }
}
