package net.onelitefeather.otis.api;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per scenario of the player-settings spec, against the real HTTP stack and an in-memory
 * database. Every test creates its own player with a random uuid, so tests are order independent.
 */
@MicronautTest(environments = "test", transactional = false)
class PlayerSettingApiTest {

    private static final String SETTINGS = "/v1/players/{player}/settings";
    private static final String SETTING = SETTINGS + "/{key}";
    private static final String PROBLEMS = "https://otis.onelitefeather.net/problems/";

    @Inject
    EmbeddedServer server;

    @Inject
    OtisPlayerRepository players;

    @Inject
    DataSource dataSource;

    private final List<UUID> createdPlayers = new ArrayList<>();

    @AfterEach
    void removeOwnFixtures() {
        createdPlayers.forEach(players::deleteById);
    }

    private UUID storePlayer() {
        UUID mojangUuid = UUID.randomUUID();
        OtisPlayer saved = players.save(new OtisPlayer(
                null, mojangUuid, "P" + mojangUuid.toString().substring(0, 8).replace('-', '_'),
                1000L, 2000L, Map.of("skin", "abc"), Locale.US));
        createdPlayers.add(saved.getUuid());
        return mojangUuid;
    }

    /** A new request specification per call: RestAssured specifications accumulate state. */
    private RequestSpecification http() {
        return RestAssured.given().baseUri("http://localhost").port(server.getPort());
    }

    private io.restassured.response.ValidatableResponse put(UUID player, String key, String json) {
        return http().contentType(ContentType.JSON).body(json)
                .when().put(SETTING, player, key).then();
    }

    private long settingRows() throws SQLException {
        try (Connection connection = dataSource.unwrap(HikariDataSource.class).getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select count(*) from player_setting")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // --- Settings are identified by player and namespaced key

    @Test
    void storedSettingCanBeReadBack() {
        UUID player = storePlayer();
        put(player, "lobby:player_hider", "{\"enabled\":true}").statusCode(201);

        http().when().get(SETTING, player, "lobby:player_hider")
                .then().statusCode(200)
                .body("key", equalTo("lobby:player_hider"))
                .body("value.enabled", equalTo(true));
    }

    @Test
    void sameKeyOfDifferentPlayersHoldsIndependentValues() {
        UUID first = storePlayer();
        UUID second = storePlayer();
        put(first, "olf:language", "\"de_de\"").statusCode(201);
        put(second, "olf:language", "\"en_us\"").statusCode(201);

        http().when().get(SETTING, first, "olf:language").then().body("value", equalTo("de_de"));
        http().when().get(SETTING, second, "olf:language").then().body("value", equalTo("en_us"));
    }

    @Test
    void scalarJsonValueIsStoredAndReturned() {
        UUID player = storePlayer();
        put(player, "olf:language", "\"de_de\"").statusCode(201);

        http().when().get(SETTING, player, "olf:language").then().statusCode(200).body("value", equalTo("de_de"));
    }

    @Test
    void jsonNullIsAValidValueAndComesBackAsNull() {
        UUID player = storePlayer();
        put(player, "olf:nickname", "null").statusCode(201);

        http().when().get(SETTING, player, "olf:nickname")
                .then().statusCode(200).body("value", nullValue()).body("version", equalTo(1));
    }

    @Test
    void numbersBooleansAndArraysRoundTrip() {
        UUID player = storePlayer();
        put(player, "lobby:mixed", "[1,2.5,true,\"x\",{\"a\":[]}]").statusCode(201);

        http().when().get(SETTING, player, "lobby:mixed")
                .then().statusCode(200)
                .body("value[0]", equalTo(1)).body("value[1]", equalTo(2.5f))
                .body("value[2]", equalTo(true)).body("value[3]", equalTo("x")).body("value[4].a", empty());
    }

    @Test
    void keyValueMayContainSlashes() {
        UUID player = storePlayer();
        http().contentType(ContentType.JSON).body("{\"rows\":3}")
                .when().put("/v1/players/" + player + "/settings/lobby:shop/layout")
                .then().statusCode(201).body("key", equalTo("lobby:shop/layout"));

        http().when().get("/v1/players/" + player + "/settings/lobby:shop/layout")
                .then().statusCode(200).body("value.rows", equalTo(3));
    }

    // --- Key namespace rules

    @Test
    void keyWithoutNamespaceIsRejectedAndNothingIsStored() throws Exception {
        UUID player = storePlayer();
        long before = settingRows();

        put(player, "player_hider", "true").statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo(PROBLEMS + "missing-namespace"));

        assertEquals(before, settingRows(), "nothing is stored");
    }

    @Test
    void minecraftNamespaceIsReserved() {
        put(storePlayer(), "minecraft:player_hider", "true").statusCode(400)
                .body("type", equalTo(PROBLEMS + "reserved-namespace"));
    }

    @Test
    void keyWithInvalidCharactersIsRejected() {
        put(storePlayer(), "Lobby:Player Hider", "true").statusCode(400)
                .body("type", equalTo(PROBLEMS + "invalid-setting-key"));
    }

    @Test
    void genericNamespaceIsAccepted() {
        put(storePlayer(), "olf:language", "\"de_de\"").statusCode(201);
    }

    @Test
    void readingAndDeletingWithInvalidKeyIsRejectedToo() {
        UUID player = storePlayer();

        http().when().get(SETTING, player, "player_hider").then().statusCode(400)
                .body("type", equalTo(PROBLEMS + "missing-namespace"));
        http().when().delete(SETTING, player, "minecraft:x").then().statusCode(400)
                .body("type", equalTo(PROBLEMS + "reserved-namespace"));
    }

    // --- Settings belong to known players

    @Test
    void putForUnknownPlayerAnswers404AndStoresNeitherSettingNorPlayer() throws Exception {
        UUID unknown = UUID.randomUUID();
        long before = settingRows();

        put(unknown, "olf:language", "\"de_de\"").statusCode(404)
                .body("type", equalTo(PROBLEMS + "player-not-found"));

        assertEquals(before, settingRows(), "no setting is stored");
        assertTrue(players.findByPlayerUuid(unknown).isEmpty(), "no player is created implicitly");
    }

    @Test
    void listForUnknownPlayerAnswers404() {
        http().when().get(SETTINGS, UUID.randomUUID()).then().statusCode(404)
                .body("type", equalTo(PROBLEMS + "player-not-found"));
    }

    @Test
    void getAndDeleteForUnknownPlayerAnswer404() {
        UUID unknown = UUID.randomUUID();

        http().when().get(SETTING, unknown, "olf:language").then().statusCode(404)
                .body("type", equalTo(PROBLEMS + "player-not-found"));
        http().when().delete(SETTING, unknown, "olf:language").then().statusCode(404)
                .body("type", equalTo(PROBLEMS + "player-not-found"));
    }

    // --- Versioned settings API

    @Test
    void createAnswers201WithKeyValueVersionAndUpdatedAt() {
        UUID player = storePlayer();
        Instant before = Instant.now().minusSeconds(60);

        String updatedAt = put(player, "lobby:player_hider", "{\"enabled\":true}").statusCode(201)
                .body("key", equalTo("lobby:player_hider"))
                .body("value.enabled", equalTo(true))
                .body("version", equalTo(1))
                .extract().path("updatedAt");

        assertTrue(Instant.parse(updatedAt).isAfter(before), "updatedAt is an ISO-8601 instant of now: " + updatedAt);
    }

    @Test
    void replacingWithDifferentValueAnswers200AndIncrementsVersion() {
        UUID player = storePlayer();
        put(player, "lobby:player_hider", "{\"enabled\":true}").statusCode(201);

        put(player, "lobby:player_hider", "{\"enabled\":false}").statusCode(200)
                .body("value.enabled", equalTo(false))
                .body("version", equalTo(2));
    }

    @Test
    void getOfMissingSettingAnswers404SettingNotFound() {
        http().when().get(SETTING, storePlayer(), "lobby:player_hider").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo(PROBLEMS + "setting-not-found"));
    }

    @Test
    void deleteAnswers204AndTheSettingIsGone() {
        UUID player = storePlayer();
        put(player, "lobby:player_hider", "true").statusCode(201);

        http().when().delete(SETTING, player, "lobby:player_hider").then().statusCode(204);

        http().when().get(SETTING, player, "lobby:player_hider").then().statusCode(404)
                .body("type", equalTo(PROBLEMS + "setting-not-found"));
    }

    // --- Listing filters by namespace

    @Test
    void listWithNamespaceParametersReturnsOnlyThoseNamespaces() {
        UUID player = storePlayer();
        put(player, "olf:language", "\"de_de\"").statusCode(201);
        put(player, "lobby:player_hider", "true").statusCode(201);
        put(player, "bedwars:shop_layout", "1").statusCode(201);

        http().queryParam("namespace", "olf", "lobby")
                .when().get(SETTINGS, player)
                .then().statusCode(200)
                .body("key", containsInAnyOrder("olf:language", "lobby:player_hider"));
    }

    @Test
    void listWithoutFilterReturnsAllSettings() {
        UUID player = storePlayer();
        put(player, "olf:language", "\"de_de\"").statusCode(201);
        put(player, "lobby:player_hider", "true").statusCode(201);
        put(player, "bedwars:shop_layout", "1").statusCode(201);

        http().when().get(SETTINGS, player)
                .then().statusCode(200)
                .body("key", contains("bedwars:shop_layout", "lobby:player_hider", "olf:language"));
    }

    @Test
    void listOfKnownPlayerWithoutSettingsIsAnEmptyList() {
        http().when().get(SETTINGS, storePlayer()).then().statusCode(200).body("$", hasSize(0));
    }

    // --- Writes are last-write-wins and idempotent

    @Test
    void repeatedPutOfSemanticallyEqualValueKeepsVersionAndUpdatedAt() {
        UUID player = storePlayer();
        String createdAt = put(player, "lobby:player_hider", "{\"b\":2,\"a\":1}").statusCode(201)
                .extract().path("updatedAt");

        put(player, "lobby:player_hider", "{\"a\":1,\"b\":2}").statusCode(200)
                .body("version", equalTo(1))
                .body("updatedAt", equalTo(createdAt));
    }

    @Test
    void laterPutWins() {
        UUID player = storePlayer();
        put(player, "olf:language", "\"de_de\"").statusCode(201);
        put(player, "olf:language", "\"fr_fr\"").statusCode(200);

        http().when().get(SETTING, player, "olf:language").then().body("value", equalTo("fr_fr"));
    }

    @Test
    void deleteOfMissingSettingAnswers204() {
        http().when().delete(SETTING, storePlayer(), "lobby:player_hider").then().statusCode(204);
    }

    // --- Setting values are bounded

    @Test
    void bodyThatIsNotJsonIsRejected() {
        put(storePlayer(), "olf:language", "{not json").statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo(PROBLEMS + "invalid-setting-value"));
    }

    @Test
    void emptyBodyIsRejected() {
        put(storePlayer(), "olf:language", "").statusCode(400)
                .body("type", equalTo(PROBLEMS + "invalid-setting-value"));
    }

    @Test
    void bodyWithTrailingContentIsRejected() {
        put(storePlayer(), "olf:language", "{} {}").statusCode(400)
                .body("type", equalTo(PROBLEMS + "invalid-setting-value"));
    }

    @Test
    void valueLargerThan64KiBAnswers413AndKeepsTheStoredSetting() {
        UUID player = storePlayer();
        put(player, "lobby:big", "{\"v\":1}").statusCode(201);
        String tooLarge = "\"" + "x".repeat(65536) + "\"";

        put(player, "lobby:big", tooLarge).statusCode(413)
                .body("type", equalTo(PROBLEMS + "setting-value-too-large"));

        http().when().get(SETTING, player, "lobby:big").then().body("value.v", equalTo(1)).body("version", equalTo(1));
    }

    @Test
    void problemResponsesCarryTheStatusAndATitle() {
        put(storePlayer(), "player_hider", "true").statusCode(400)
                .body("status", equalTo(400))
                .body("title", equalTo("Missing key namespace"))
                .body("type", endsWith("/missing-namespace"));
    }
}
