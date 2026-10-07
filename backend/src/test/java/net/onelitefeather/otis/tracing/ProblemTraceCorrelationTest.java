package net.onelitefeather.otis.tracing;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End to end: real server spans of an in-memory SDK, real HTTP requests.
 */
@MicronautTest(environments = "test", transactional = false)
@Property(name = "otis.test.tracing", value = "memory")
@Property(name = "otel.traces.exporter", value = "memory")
class ProblemTraceCorrelationTest {

    @Inject
    RequestSpecification spec;

    @Inject
    StartedSpans startedSpans;

    private SpanData serverSpanFor(String path) {
        return startedSpans.serverSpans().stream()
                .filter(span -> span.getName().contains(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no server span for " + path + ", got "
                        + startedSpans.serverSpans().stream().map(SpanData::getName).toList()));
    }

    @Test
    void problemTraceIdEqualsTraceIdOfTheServerSpan() {
        String traceId = spec.when().get("/test-failures/domain")
                .then().statusCode(409)
                .extract().path("traceId");

        assertEquals(serverSpanFor("/test-failures/domain").getTraceId(), traceId,
                "traceId in the problem must be the trace id of the request's server span");
    }

    @Test
    void unexpectedFailureMarksServerSpanAsErrorAndRecordsTheException() {
        spec.when().get("/test-failures/unexpected").then().statusCode(500);

        SpanData span = serverSpanFor("/test-failures/unexpected");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), "span status of a 5xx");
        assertFalse(span.getEvents().stream().noneMatch(e -> "exception".equals(e.getName())),
                "the failure must be recorded on the server span");
    }
}
