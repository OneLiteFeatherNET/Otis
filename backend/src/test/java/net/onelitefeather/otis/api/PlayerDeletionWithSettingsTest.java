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
 * Scenario "Deleting a player is unaffected by settings": the existing delete endpoint answers as before
 * and the database cascade removes the player's settings with it.
 */
@MicronautTest(environments = "test", transactional = false)
class PlayerDeletionWithSettingsTest {

    @Inject
    EmbeddedServer server;

    @Inject
    OtisPlayerRepository players;

    @Inject
    DataSource dataSource;

    private RequestSpecification http() {
        return RestAssured.given().baseUri("http://localhost").port(server.getPort());
    }

    private long settingRowsOf(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.unwrap(HikariDataSource.class).getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from player_setting where player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void deletingAPlayerAnswersAsBeforeAndRemovesItsSettings() throws Exception {
        UUID mojangUuid = UUID.randomUUID();
        OtisPlayer player = players.save(new OtisPlayer(
                null, mojangUuid, "Settings_Owner", 1000L, 2000L, Map.of("skin", "abc"), Locale.US));
        http().contentType(ContentType.JSON).body("{\"enabled\":true}")
                .put("/v1/players/{player}/settings/{key}", mojangUuid, "lobby:player_hider").then().statusCode(201);
        http().contentType(ContentType.JSON).body("\"de_de\"")
                .put("/v1/players/{player}/settings/{key}", mojangUuid, "olf:language").then().statusCode(201);
        assertEquals(2, settingRowsOf(player.getUuid()), "precondition: the player has two settings");

        http().contentType(ContentType.JSON)
                .when().post("/otis/delete/{owner}", player.getUuid())
                .then().statusCode(200)
                .body("playerName", equalTo("Settings_Owner"))
                .body("playerUuid", equalTo(mojangUuid.toString()));

        assertEquals(0, settingRowsOf(player.getUuid()), "the player's settings are deleted with it");
        http().when().get("/otis/byId/{owner}", mojangUuid).then().statusCode(404);
        http().when().get("/v1/players/{player}/settings", mojangUuid).then().statusCode(404)
                .body("type", equalTo("https://otis.onelitefeather.net/problems/player-not-found"));
    }
}
