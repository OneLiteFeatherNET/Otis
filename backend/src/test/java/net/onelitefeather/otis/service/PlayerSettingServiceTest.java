package net.onelitefeather.otis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.database.entity.PlayerSetting;
import net.onelitefeather.otis.dto.PlayerSettingDTO;
import net.onelitefeather.otis.problem.MissingNamespaceProblem;
import net.onelitefeather.otis.problem.PlayerNotFoundProblem;
import net.onelitefeather.otis.problem.SettingNotFoundProblem;
import net.onelitefeather.otis.problem.SettingValueTooLargeProblem;
import net.onelitefeather.otis.settings.SettingJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSettingServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final Key HIDER = Key.key("lobby", "player_hider");

    private final ObjectMapper mapper = SettingJson.newMapper();
    private final FakePlayerSettingRepository repository = new FakePlayerSettingRepository();
    private final MutableClock clock = new MutableClock(START);
    private final UUID player = UUID.randomUUID();
    private PlayerSettingService service;

    @BeforeEach
    void setUp() {
        repository.addPlayer(player);
        service = new PlayerSettingService(repository, mapper, clock, OpenTelemetry.noop());
    }

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    @Test
    void putOfNewSettingCreatesItWithVersionOne() throws Exception {
        PutResult result = service.put(player, "lobby:player_hider", json("{\"enabled\":true}"));

        PutResult.Created created = assertInstanceOf(PutResult.Created.class, result, "first write creates");
        assertEquals(new PlayerSettingDTO(HIDER, Map.of("enabled", true), 1, START), created.setting(),
                "created setting");
    }

    @Test
    void putOfDifferentValueUpdatesVersionAndTimestamp() throws Exception {
        service.put(player, "lobby:player_hider", json("{\"enabled\":true}"));
        clock.advance(Duration.ofMinutes(5));

        PutResult result = service.put(player, "lobby:player_hider", json("{\"enabled\":false}"));

        PutResult.Updated updated = assertInstanceOf(PutResult.Updated.class, result, "changed value updates");
        assertEquals(new PlayerSettingDTO(HIDER, Map.of("enabled", false), 2, START.plus(Duration.ofMinutes(5))),
                updated.setting(), "updated setting");
    }

    @Test
    void putOfSemanticallyEqualValueChangesNothing() throws Exception {
        service.put(player, "lobby:player_hider", json("{\"b\":2,\"a\":1}"));
        clock.advance(Duration.ofMinutes(5));

        PutResult result = service.put(player, "lobby:player_hider", json("{\"a\":1,\"b\":2}"));

        PutResult.Unchanged unchanged = assertInstanceOf(PutResult.Unchanged.class, result,
                "key order must not matter");
        assertEquals(1, unchanged.setting().version(), "version stays");
        assertEquals(START, unchanged.setting().updatedAt(), "updatedAt stays");
    }

    @Test
    void putAcceptsScalarJsonValues() throws Exception {
        PutResult result = service.put(player, "olf:language", json("\"de_de\""));

        assertEquals("de_de", result.setting().value(), "string value");
    }

    @Test
    void putAcceptsJsonNullAsValue() throws Exception {
        PutResult result = service.put(player, "olf:language", json("null"));

        assertEquals(null, result.setting().value(), "null value is a value");
        assertEquals(1, repository.all().size(), "the null value is stored");
    }

    @Test
    void sameKeyForDifferentPlayersStoresIndependentSettings() throws Exception {
        UUID other = UUID.randomUUID();
        repository.addPlayer(other);

        service.put(player, "olf:language", json("\"de_de\""));
        service.put(other, "olf:language", json("\"en_us\""));

        assertEquals("de_de", service.get(player, "olf:language").value(), "first player's value");
        assertEquals("en_us", service.get(other, "olf:language").value(), "second player's value");
    }

    @Test
    void putForUnknownPlayerFailsAndStoresNothing() throws Exception {
        assertThrows(PlayerNotFoundProblem.class,
                () -> service.put(UUID.randomUUID(), "olf:language", json("\"de_de\"")), "unknown player");

        assertTrue(repository.all().isEmpty(), "nothing is stored for an unknown player");
    }

    @Test
    void putOfValueLargerThanLimitFailsAndLeavesStoredSettingUnchanged() throws Exception {
        service.put(player, "lobby:player_hider", json("{\"enabled\":true}"));
        String tooLarge = "\"" + "x".repeat(PlayerSettingService.MAX_VALUE_BYTES) + "\"";

        assertThrows(SettingValueTooLargeProblem.class,
                () -> service.put(player, "lobby:player_hider", json(tooLarge)), "value above 64 KiB");

        assertEquals(Map.of("enabled", true), service.get(player, "lobby:player_hider").value(),
                "stored value stays");
    }

    @Test
    void putOfValueExactlyAtLimitIsAccepted() throws Exception {
        String atLimit = "\"" + "x".repeat(PlayerSettingService.MAX_VALUE_BYTES - 2) + "\"";

        PutResult result = service.put(player, "lobby:big", json(atLimit));

        assertInstanceOf(PutResult.Created.class, result, "65536 serialized bytes are allowed");
    }

    @Test
    void putWithKeyWithoutNamespaceFails() throws Exception {
        assertThrows(MissingNamespaceProblem.class,
                () -> service.put(player, "player_hider", json("true")), "no namespace");

        assertTrue(repository.all().isEmpty(), "nothing is stored");
    }

    @Test
    void putRetriesOnceWhenAConcurrentFirstWriteWinsTheRace() throws Exception {
        repository.failNextSave(
                new RuntimeException("insert failed", new SQLException("duplicate key", "23505")),
                () -> repository.insertDirectly(new PlayerSetting(
                        repository.findPlayerIds(player).getFirst(), HIDER, mapper.valueToTree(Map.of("enabled", true)),
                        1, START)));

        PutResult result = service.put(player, "lobby:player_hider", json("{\"enabled\":false}"));

        assertInstanceOf(PutResult.Updated.class, result, "after the retry the setting exists and is updated");
        assertEquals(1, repository.all().size(), "exactly one row");
        assertEquals(2, result.setting().version(), "version continues from the competing write");
    }

    @Test
    void putRetryOfEqualCompetingValueIsUnchanged() throws Exception {
        repository.failNextSave(
                new RuntimeException("insert failed", new SQLException("duplicate key", "23505")),
                () -> repository.insertDirectly(new PlayerSetting(
                        repository.findPlayerIds(player).getFirst(), HIDER, mapper.valueToTree(Map.of("enabled", true)),
                        1, START)));

        PutResult result = service.put(player, "lobby:player_hider", json("{\"enabled\":true}"));

        assertInstanceOf(PutResult.Unchanged.class, result, "both writers wrote the same value");
    }

    @Test
    void putDoesNotRetryOtherFailures() throws Exception {
        RuntimeException failure = new RuntimeException("connection lost", new SQLException("down", "08006"));
        repository.failNextSave(failure, () -> { });

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.put(player, "lobby:player_hider", json("true")), "unrelated failure");

        assertEquals(failure, thrown, "the original failure propagates");
    }

    @Test
    void getReturnsStoredSetting() throws Exception {
        service.put(player, "lobby:player_hider", json("{\"enabled\":true}"));

        assertEquals(Map.of("enabled", true), service.get(player, "lobby:player_hider").value(), "stored value");
    }

    @Test
    void getOfMissingSettingFails() {
        assertThrows(SettingNotFoundProblem.class, () -> service.get(player, "lobby:player_hider"),
                "no such setting");
    }

    @Test
    void getForUnknownPlayerFails() {
        assertThrows(PlayerNotFoundProblem.class, () -> service.get(UUID.randomUUID(), "lobby:player_hider"),
                "unknown player");
    }

    @Test
    void deleteOfExistingSettingRemovesIt() throws Exception {
        service.put(player, "lobby:player_hider", json("true"));

        assertTrue(service.delete(player, "lobby:player_hider"), "reports that a setting was removed");
        assertThrows(SettingNotFoundProblem.class, () -> service.get(player, "lobby:player_hider"),
                "setting is gone");
    }

    @Test
    void deleteOfMissingSettingSucceeds() {
        assertFalse(service.delete(player, "lobby:player_hider"), "nothing to remove is not an error");
    }

    @Test
    void deleteForUnknownPlayerFails() {
        assertThrows(PlayerNotFoundProblem.class, () -> service.delete(UUID.randomUUID(), "lobby:player_hider"),
                "unknown player");
    }

    @Test
    void listWithoutFilterReturnsAllSettingsSortedByKey() throws Exception {
        service.put(player, "lobby:player_hider", json("true"));
        service.put(player, "olf:language", json("\"de_de\""));
        service.put(player, "bedwars:shop_layout", json("1"));

        List<String> keys = service.list(player, List.of()).stream().map(s -> s.key().asString()).toList();

        assertEquals(List.of("bedwars:shop_layout", "lobby:player_hider", "olf:language"), keys, "all settings");
    }

    @Test
    void listWithNamespacesReturnsOnlyMatchingSettings() throws Exception {
        service.put(player, "lobby:player_hider", json("true"));
        service.put(player, "olf:language", json("\"de_de\""));
        service.put(player, "bedwars:shop_layout", json("1"));

        List<String> keys = service.list(player, List.of("olf", "lobby")).stream()
                .map(s -> s.key().asString()).toList();

        assertEquals(List.of("lobby:player_hider", "olf:language"), keys, "only requested namespaces");
    }

    @Test
    void listOfPlayerWithoutSettingsIsEmpty() {
        assertTrue(service.list(player, List.of()).isEmpty(), "known player without settings");
    }

    @Test
    void listForUnknownPlayerFails() {
        assertThrows(PlayerNotFoundProblem.class, () -> service.list(UUID.randomUUID(), List.of()),
                "unknown player");
    }
}
