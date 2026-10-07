package net.onelitefeather.otis.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.problem.OtisProblemException;
import net.onelitefeather.otis.problem.PlayerNotFoundProblem;
import net.onelitefeather.otis.problem.SettingNotFoundProblem;

import java.util.Collection;
import java.util.UUID;

/**
 * Wraps one settings use case in an internal span. Records who (player uuid), what (namespace, key)
 * and how it ended (outcome) - never the setting value.
 * <p>
 * Expected outcomes (not found, rejected input) leave the span status unset; only unexpected
 * exceptions mark it as error. The exception itself is recorded once, by the problem handler.
 */
final class SettingSpans {

    static final String INSTRUMENTATION_SCOPE = "net.onelitefeather.otis";

    static final AttributeKey<String> PLAYER_UUID = AttributeKey.stringKey("otis.player.uuid");
    static final AttributeKey<String> NAMESPACE = AttributeKey.stringKey("otis.setting.namespace");
    static final AttributeKey<java.util.List<String>> NAMESPACES = AttributeKey.stringArrayKey("otis.setting.namespaces");
    static final AttributeKey<String> KEY = AttributeKey.stringKey("otis.setting.key");
    static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("otis.setting.outcome");

    static final String CREATED = "created";
    static final String UPDATED = "updated";
    static final String UNCHANGED = "unchanged";
    static final String DELETED = "deleted";
    static final String FOUND = "found";
    static final String NOT_FOUND = "not_found";
    static final String REJECTED = "rejected";

    private final Tracer tracer;

    SettingSpans(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
    }

    /** The span of a running use case, as seen by the code inside it. */
    static final class Observation {

        private final Span span;
        private boolean outcomeSet;

        private Observation(Span span) {
            this.span = span;
        }

        void key(Key key) {
            span.setAttribute(NAMESPACE, key.namespace());
            span.setAttribute(KEY, key.asString());
        }

        void namespaces(Collection<String> namespaces) {
            if (!namespaces.isEmpty()) {
                span.setAttribute(NAMESPACES, java.util.List.copyOf(namespaces));
            }
        }

        void outcome(String outcome) {
            span.setAttribute(OUTCOME, outcome);
            outcomeSet = true;
        }
    }

    @FunctionalInterface
    interface Body<T> {
        T run(Observation observation);
    }

    <T> T inSpan(String name, UUID playerUuid, Body<T> body) {
        Span span = tracer.spanBuilder(name)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(PLAYER_UUID, playerUuid.toString())
                .startSpan();
        Observation observation = new Observation(span);
        try (Scope ignored = span.makeCurrent()) {
            return body.run(observation);
        } catch (OtisProblemException problem) {
            if (!observation.outcomeSet) {
                observation.outcome(switch (problem) {
                    case PlayerNotFoundProblem _, SettingNotFoundProblem _ -> NOT_FOUND;
                    default -> REJECTED;
                });
            }
            throw problem;
        } catch (RuntimeException unexpected) {
            span.setStatus(StatusCode.ERROR);
            throw unexpected;
        } finally {
            span.end();
        }
    }
}
