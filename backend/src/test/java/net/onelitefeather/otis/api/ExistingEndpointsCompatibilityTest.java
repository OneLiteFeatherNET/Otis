package net.onelitefeather.otis.api;

import io.micronaut.http.HttpStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

/**
 * Characterization tests: pin paths, methods and status codes of the existing endpoints so the
 * error format switch can be proven non-breaking for status-code based clients.
 */
@MicronautTest(environments = "test", transactional = false)
class ExistingEndpointsCompatibilityTest {

    @Inject
    RequestSpecification spec;

    @Inject
    OtisPlayerRepository repository;

    private final List<UUID> createdPlayers = new ArrayList<>();

    @AfterEach
    void removeOwnFixtures() {
        createdPlayers.forEach(repository::deleteById);
    }

    private OtisPlayer storePlayer(String name) {
        OtisPlayer saved = repository.save(new OtisPlayer(
                null, UUID.randomUUID(), name, 1000L, 2000L, Map.of("skin", "abc"), Locale.US));
        createdPlayers.add(saved.getUuid());
        return saved;
    }

    @Test
    void getByIdForUnknownPlayerAnswers404() {
        spec.when().get("/otis/byId/{owner}", UUID.randomUUID())
                .then().statusCode(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void getByNameForUnknownPlayerAnswers404() {
        spec.when().get("/otis/byName/{owner}", UUID.randomUUID())
                .then().statusCode(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void searchByIdForUnknownPlayerAnswers404() {
        spec.when().get("/search/byId/{id}", UUID.randomUUID())
                .then().statusCode(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void searchByNameForUnknownPlayerAnswers404() {
        spec.when().get("/search/byName/{name}", "Unknown_Player")
                .then().statusCode(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void deleteForUnknownPlayerAnswers404() {
        spec.contentType("application/json")
                .when().post("/otis/delete/{owner}", UUID.randomUUID())
                .then().statusCode(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void updateWithMismatchingOwnerAnswers400() {
        UUID owner = UUID.randomUUID();
        String body = """
                {"playerDTO":{"playerUuid":"%s","playerName":"Mismatch_1","firstJoin":1,"lastJoin":2,"profileTextures":{},"locale":"en-US"}}
                """.formatted(UUID.randomUUID());

        spec.contentType("application/json").body(body)
                .when().post("/otis/update/{owner}", owner)
                .then().statusCode(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void addPlayerWithWrappedBodyAnswers200WithStoredPlayer() {
        UUID mojangUuid = UUID.randomUUID();
        String body = """
                {"playerDTO":{"playerUuid":"%s","playerName":"Added_Player","firstJoin":1,"lastJoin":2,"profileTextures":{},"locale":"en-US"}}
                """.formatted(mojangUuid);

        String storedUuid = spec.contentType("application/json").body(body)
                .when().post("/otis")
                .then()
                .statusCode(HttpStatus.OK.getCode())
                .body("playerUuid", equalTo(mojangUuid.toString()))
                .body("playerName", equalTo("Added_Player"))
                .extract().path("uuid");
        createdPlayers.add(UUID.fromString(storedUuid));
    }

    @Test
    void getByIdForStoredPlayerAnswers200WithUnchangedBodyMembers() {
        OtisPlayer stored = storePlayer("Known_Player");

        spec.when().get("/otis/byId/{owner}", stored.getPlayerUuid())
                .then()
                .statusCode(HttpStatus.OK.getCode())
                .body("keySet()", containsInAnyOrder(
                        "uuid", "playerUuid", "playerName", "firstJoin", "lastJoin",
                        "profileTextures", "locale"))
                .body("uuid", equalTo(stored.getUuid().toString()))
                .body("playerUuid", equalTo(stored.getPlayerUuid().toString()))
                .body("playerName", equalTo("Known_Player"))
                .body("firstJoin", equalTo(1000))
                .body("lastJoin", equalTo(2000))
                .body("profileTextures.skin", equalTo("abc"))
                .body("locale", equalTo("en-US"));
    }
}
