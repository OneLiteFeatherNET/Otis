package net.onelitefeather.otis.api;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.hasKey;

@MicronautTest(environments = "test", transactional = false)
class ProblemDetailsResponseTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Inject
    RequestSpecification spec;

    @Test
    void unknownRouteAnswersProblemJsonWith404() {
        spec.when().get("/does-not-exist")
                .then()
                .statusCode(404)
                .contentType(startsWith(PROBLEM_JSON))
                .body("status", equalTo(404))
                .body("$", hasKey("type"))
                .body("$", hasKey("title"));
    }

    @Test
    void unknownRouteUsesAboutBlankTypeAndReasonPhraseTitle() {
        spec.when().get("/does-not-exist")
                .then()
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Not Found"));
    }

    @Test
    void malformedJsonBodyAnswersProblemJsonWith400() {
        spec.contentType("application/json").body("{not json")
                .when().post("/otis")
                .then()
                .statusCode(400)
                .contentType(startsWith(PROBLEM_JSON))
                .body("status", equalTo(400));
    }

    @Test
    void unexpectedExceptionAnswersProblemJsonWith500() {
        spec.when().get("/test-failures/unexpected")
                .then()
                .statusCode(500)
                .contentType(startsWith(PROBLEM_JSON))
                .body("status", equalTo(500))
                .body("type", equalTo("about:blank"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternals() {
        String body = spec.when().get("/test-failures/unexpected")
                .then().extract().asString();

        org.hamcrest.MatcherAssert.assertThat("exception message must not leak",
                body, not(containsString(FailingEndpointsController.SECRET_MESSAGE)));
        org.hamcrest.MatcherAssert.assertThat("exception class name must not leak",
                body, not(containsString("IllegalStateException")));
        org.hamcrest.MatcherAssert.assertThat("stack trace must not leak",
                body, not(containsString("\tat ")));
    }

    @Test
    void problemHasNoTraceIdWhileTracingIsDisabled() {
        spec.when().get("/does-not-exist")
                .then()
                .body("$", not(hasKey("traceId")));
    }
}
