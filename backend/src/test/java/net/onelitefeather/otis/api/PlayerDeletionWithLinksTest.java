package net.onelitefeather.otis.api;

import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scenario "Player deletion cascades": the existing delete endpoint answers as before and the database
 * cascade removes the player's links and open codes with it.
 */
@MicronautTest(environments = "test", transactional = false)
class PlayerDeletionWithLinksTest {

    @Inject
    EmbeddedServer server;

    @Inject
    OtisPlayerRepository players;

    @Inject
    DataSource dataSource;

    private RequestSpecification http() {
        return RestAssured.given().baseUri("http://localhost").port(server.getPort()).contentType(ContentType.JSON);
    }

    private long rowsOf(String table, UUID playerId) throws SQLException {
        try (Connection connection = dataSource.unwrap(HikariDataSource.class).getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from " + table + " where player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void deletingAPlayerAnswersAsBeforeAndRemovesLinksAndCodes() throws Exception {
        UUID mojangUuid = UUID.randomUUID();
        String account = "cascade-" + mojangUuid;
        OtisPlayer player = players.save(new OtisPlayer(
                null, mojangUuid, "Links_Owner", 1000L, 2000L, Map.of("skin", "abc"), Locale.US));
        String code = http().body(Map.of("provider", "discord"))
                .post("/v1/players/{player}/link-codes", mojangUuid).then().statusCode(201).extract().path("code");
        http().body(Map.of("code", code, "provider", "discord", "externalId", account))
                .post("/v1/link-codes/redeem").then().statusCode(201);
        http().body(Map.of("provider", "twitch")).post("/v1/players/{player}/link-codes", mojangUuid).then().statusCode(201);
        assertEquals(1, rowsOf("account_link", player.getUuid()), "precondition: one link");
        assertEquals(2, rowsOf("link_code", player.getUuid()), "precondition: two codes");

        http().when().post("/otis/delete/{owner}", player.getUuid())
                .then().statusCode(200)
                .body("playerName", equalTo("Links_Owner"))
                .body("playerUuid", equalTo(mojangUuid.toString()));

        assertEquals(0, rowsOf("account_link", player.getUuid()), "the links are deleted with the player");
        assertEquals(0, rowsOf("link_code", player.getUuid()), "the codes are deleted with the player");
        http().when().get("/v1/links/{provider}/{externalId}", "discord", account).then().statusCode(404)
                .body("type", equalTo("https://otis.onelitefeather.net/problems/link-not-found"));
    }
}
