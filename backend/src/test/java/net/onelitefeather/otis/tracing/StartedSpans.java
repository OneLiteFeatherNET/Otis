package net.onelitefeather.otis.tracing;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps every span the SDK starts, so tests can inspect a request's server span as soon as the
 * response arrives, without waiting for the span to end.
 */
public final class StartedSpans implements SpanProcessor {

    private final List<ReadableSpan> spans = new CopyOnWriteArrayList<>();

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        spans.add(span);
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // nothing to do: spans are kept from onStart on
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    /**
     * @return snapshots of all server spans started so far
     */
    public List<SpanData> serverSpans() {
        return spans.stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .map(ReadableSpan::toSpanData)
                .toList();
    }
}
