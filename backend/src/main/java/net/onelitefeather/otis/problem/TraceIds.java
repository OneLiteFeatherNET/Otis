package net.onelitefeather.otis.problem;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

import java.util.Optional;

/** Reads the W3C trace id of the span that is current on the calling thread. */
final class TraceIds {

    private TraceIds() {
    }

    /**
     * @return the trace id of the active span, or empty while no valid trace is being recorded
     */
    static Optional<String> current() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? Optional.of(context.getTraceId()) : Optional.empty();
    }
}
