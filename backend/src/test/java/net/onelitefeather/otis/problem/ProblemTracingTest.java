package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.problem.conf.ProblemConfiguration;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.zalando.problem.Problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace correlation of problem responses, driven by an in-memory OpenTelemetry SDK. Each test gets
 * its own extension instance, so no tracing state is shared between tests.
 */
class ProblemTracingTest {

    @RegisterExtension
    final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

    private static final ProblemConfiguration CONFIGURATION = new ProblemConfiguration() {
        @Override
        public boolean isStackTrace() {
            return false;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    };

    private final OtisProblemBodyProvider provider = new OtisProblemBodyProvider(CONFIGURATION, "otlp");

    private static Problem notFoundProblem(OtisProblemBodyProvider provider) {
        ErrorContext context = ErrorContext.builder(HttpRequest.GET("/missing")).build();
        return provider.body(context, HttpResponse.notFound());
    }

    private Problem notFoundProblem() {
        return notFoundProblem(provider);
    }

    @Test
    void problemCarriesTraceIdOfTheActiveSpan() {
        Span span = otel.getOpenTelemetry().getTracer("test").spanBuilder("server").startSpan();
        Problem problem;
        try (Scope ignored = span.makeCurrent()) {
            problem = notFoundProblem();
        } finally {
            span.end();
        }

        assertEquals(span.getSpanContext().getTraceId(), problem.getParameters().get("traceId"),
                "traceId must equal the trace id of the active span");
    }

    @Test
    void problemHasNoTraceIdWhileTracingIsConfiguredOff() {
        var tracingOff = new OtisProblemBodyProvider(CONFIGURATION, "none");
        Span span = otel.getOpenTelemetry().getTracer("test").spanBuilder("server").startSpan();
        Problem problem;
        try (Scope ignored = span.makeCurrent()) {
            problem = notFoundProblem(tracingOff);
        } finally {
            span.end();
        }

        assertFalse(problem.getParameters().containsKey("traceId"),
                "traceId must be omitted when no trace exporter is configured");
    }

    @Test
    void problemHasNoTraceIdWithoutActiveSpan() {
        Problem problem = notFoundProblem();

        assertFalse(problem.getParameters().containsKey("traceId"),
                "traceId must be omitted when no span is active");
    }

    @Test
    void problemHasNoTraceIdForInvalidSpan() {
        Problem problem;
        try (Scope ignored = Span.getInvalid().makeCurrent()) {
            problem = notFoundProblem();
        }

        assertFalse(problem.getParameters().containsKey("traceId"),
                "traceId must be omitted when the span context is invalid");
    }

    @Test
    void clientErrorDoesNotMarkSpanAsError() {
        Span span = otel.getOpenTelemetry().getTracer("test").spanBuilder("server").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            notFoundProblem();
        } finally {
            span.end();
        }

        SpanData data = otel.getSpans().getFirst();
        assertTrue(data.getStatus().getStatusCode() != StatusCode.ERROR,
                "a 404 is an expected outcome and must not set the span status to ERROR");
        assertTrue(data.getEvents().isEmpty(), "a 404 must not record an exception event");
    }
}
