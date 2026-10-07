package net.onelitefeather.otis.api;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.startsWith;

@MicronautTest(environments = "test", transactional = false)
class ConstraintViolationResponseTest {

    @Inject
    RequestSpecification spec;

    // the API wraps the player in a "playerDTO" member (see java-client spec, addPlayer)
    private static String playerNamed(String name) {
        return """
                {"playerDTO":{"playerUuid":"%s","playerName":"%s","firstJoin":1,"lastJoin":2,"profileTextures":{},"locale":"en-US"}}
                """.formatted(UUID.randomUUID(), name);
    }

    @Test
    void invalidBodyAnswers400WithConstraintViolationType() {
        spec.contentType("application/json").body(playerNamed("ab"))
                .when().post("/otis")
                .then()
                .statusCode(400)
                .contentType(startsWith("application/problem+json"))
                .body("type", equalTo("https://otis.onelitefeather.net/problems/constraint-violation"))
                .body("status", equalTo(400));
    }

    @Test
    void violationsNameTheInvalidFieldAndMessage() {
        spec.contentType("application/json").body(playerNamed("ab"))
                .when().post("/otis")
                .then()
                .body("violations.field", hasItem(containsString("playerName")))
                .body("violations.message", hasItem("Username must be between 3 and 16 characters."));
    }

    @Test
    void oneViolationEntryPerViolatedConstraint() {
        // "a!" violates both @Size (too short) and @Pattern (illegal character)
        spec.contentType("application/json").body(playerNamed("a!"))
                .when().post("/otis")
                .then()
                .body("violations", hasSize(2))
                .body("violations.field", not(hasItem(emptyOrNullString())));
    }
}
