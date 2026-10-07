package net.onelitefeather.otis.client;

import net.onelitefeather.otis.client.invoker.ApiException;
import net.onelitefeather.otis.client.model.ProblemDetail;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemDetailsTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private static HttpHeaders contentType(String value) {
        return HttpHeaders.of(Map.of("Content-Type", List.of(value)), (name, v) -> true);
    }

    private static ApiException failure(int code, String contentType, String body) {
        return new ApiException(code, "failed", contentType == null ? null : contentType(contentType), body);
    }

    @Test
    void readsStandardMembersOfAProblem() {
        var body = """
                {"type":"https://otis.onelitefeather.net/problems/sample","title":"Sample","status":409,
                 "detail":"It conflicts.","instance":"/otis/x"}
                """;

        Optional<ProblemDetail> problem = ProblemDetails.from(failure(409, PROBLEM_JSON, body));

        assertTrue(problem.isPresent(), "a problem document must be recognised");
        assertEquals("https://otis.onelitefeather.net/problems/sample", problem.get().getType(), "type");
        assertEquals("Sample", problem.get().getTitle(), "title");
        assertEquals(409, problem.get().getStatus(), "status");
        assertEquals("It conflicts.", problem.get().getDetail(), "detail");
        assertEquals("/otis/x", problem.get().getInstance(), "instance");
    }

    @Test
    void readsTraceIdAndViolations() {
        var body = """
                {"type":"https://otis.onelitefeather.net/problems/constraint-violation","title":"Constraint Violation",
                 "status":400,"traceId":"4bf92f3577b34da6a3ce929d0e0e4736",
                 "violations":[{"field":"add.playerDTO.playerName","message":"too short"}]}
                """;

        ProblemDetail problem = ProblemDetails.from(failure(400, PROBLEM_JSON, body)).orElseThrow();

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", problem.getTraceId(), "traceId");
        assertEquals(1, problem.getViolations().size(), "violation count");
        assertEquals("add.playerDTO.playerName", problem.getViolations().getFirst().getField(), "violation field");
        assertEquals("too short", problem.getViolations().getFirst().getMessage(), "violation message");
    }

    @Test
    void keepsUnknownExtensionMembers() {
        var body = """
                {"type":"about:blank","title":"Not Found","status":404,"key":"a.b","count":3}
                """;

        ProblemDetail problem = ProblemDetails.from(failure(404, PROBLEM_JSON, body)).orElseThrow();

        assertEquals("a.b", problem.getAdditionalProperties().get("key"), "string extension member");
        assertEquals(3, problem.getAdditionalProperties().get("count"), "number extension member");
    }

    @Test
    void acceptsContentTypeWithParameters() {
        var body = "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}";

        Optional<ProblemDetail> problem = ProblemDetails.from(
                failure(404, "application/problem+json; charset=UTF-8", body));

        assertTrue(problem.isPresent(), "media type parameters must not matter");
    }

    @Test
    void emptyForHtmlErrorPageFromAProxy() {
        Optional<ProblemDetail> problem = ProblemDetails.from(
                failure(502, "text/html", "<html><body>Bad Gateway</body></html>"));

        assertTrue(problem.isEmpty(), "a non-problem content type must yield an empty result");
    }

    @Test
    void emptyForPlainJsonThatIsNotAProblem() {
        Optional<ProblemDetail> problem = ProblemDetails.from(
                failure(500, "application/json", "{\"error\":\"boom\"}"));

        assertTrue(problem.isEmpty(), "application/json is not a problem document");
    }

    @Test
    void emptyWhenBodyIsNotJsonDespiteProblemContentType() {
        Optional<ProblemDetail> problem = ProblemDetails.from(failure(500, PROBLEM_JSON, "not json {"));

        assertTrue(problem.isEmpty(), "an unparsable body must not throw");
    }

    @Test
    void emptyForEmptyBody() {
        assertTrue(ProblemDetails.from(failure(500, PROBLEM_JSON, "")).isEmpty(), "empty body");
        assertTrue(ProblemDetails.from(failure(500, PROBLEM_JSON, null)).isEmpty(), "missing body");
    }

    @Test
    void emptyWhenThereAreNoResponseHeaders() {
        Optional<ProblemDetail> problem = ProblemDetails.from(failure(500, null, "{\"title\":\"x\",\"status\":500}"));

        assertTrue(problem.isEmpty(), "without a content type the body cannot be trusted to be a problem");
    }
}
