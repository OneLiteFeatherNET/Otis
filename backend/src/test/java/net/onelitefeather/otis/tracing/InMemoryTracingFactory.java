package net.onelitefeather.otis.tracing;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.tracing.opentelemetry.DefaultOpenTelemetryFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import jakarta.inject.Singleton;

/**
 * Replaces the OpenTelemetry SDK with an in-memory one for tests that opt in with
 * {@code otis.test.tracing=memory}. Every application context gets its own instance.
 */
@Factory
@Requires(property = "otis.test.tracing", value = "memory")
class InMemoryTracingFactory {

    @Singleton
    StartedSpans startedSpans() {
        return new StartedSpans();
    }

    @Singleton
    @Replaces(bean = OpenTelemetry.class, factory = DefaultOpenTelemetryFactory.class)
    OpenTelemetry openTelemetry(StartedSpans startedSpans) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(startedSpans).build())
                .build();
    }
}
