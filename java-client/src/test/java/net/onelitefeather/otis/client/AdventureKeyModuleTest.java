package net.onelitefeather.otis.client;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.client.invoker.ApiClient;
import net.onelitefeather.otis.client.model.PlayerSettingDTO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdventureKeyModuleTest {

    private final ObjectMapper mapper = OtisClients.newApiClient().getObjectMapper();

    @Test
    void writesKeyAsPlainString() throws Exception {
        assertEquals("\"lobby:player_hider\"", mapper.writeValueAsString(Key.key("lobby", "player_hider")),
                "wire format of a key");
    }

    @Test
    void readsKeyFromPlainString() throws Exception {
        assertEquals(Key.key("olf", "language"), mapper.readValue("\"olf:language\"", Key.class),
                "key read from its wire format");
    }

    @Test
    void rejectsStringThatIsNotAKey() {
        assertThrows(JsonMappingException.class, () -> mapper.readValue("\"Not A Key:x\"", Key.class),
                "invalid Adventure key syntax");
    }

    @Test
    void readsSettingResponseWithKeyAndArbitraryValue() throws Exception {
        String json = """
                {"key":"lobby:player_hider","value":{"enabled":true,"list":[1,2]},"version":3,
                 "updatedAt":"2026-01-01T12:00:00.123456Z"}
                """;

        PlayerSettingDTO setting = mapper.readValue(json, PlayerSettingDTO.class);

        assertEquals(Key.key("lobby", "player_hider"), setting.getKey(), "key");
        assertEquals(Map.of("enabled", true, "list", java.util.List.of(1, 2)), setting.getValue(), "value");
        assertEquals(3L, setting.getVersion(), "version");
        assertEquals(OffsetDateTime.parse("2026-01-01T12:00:00.123456Z"), setting.getUpdatedAt(), "updatedAt");
    }

    @Test
    void settingWithJsonNullValueKeepsNullValue() throws Exception {
        PlayerSettingDTO setting = mapper.readValue(
                "{\"key\":\"olf:nick\",\"value\":null,\"version\":1,\"updatedAt\":\"2026-01-01T12:00:00Z\"}",
                PlayerSettingDTO.class);

        assertEquals(null, setting.getValue(), "null value");
        assertEquals(Key.key("olf", "nick"), setting.getKey(), "key");
    }

    @Test
    void plainApiClientDoesNotKnowKeysUntilConfigured() {
        ObjectMapper plain = new ApiClient().getObjectMapper();

        assertThrows(JsonMappingException.class, () -> plain.readValue("\"olf:language\"", Key.class),
                "documents why OtisClients is the entry point for settings calls");
    }
}
