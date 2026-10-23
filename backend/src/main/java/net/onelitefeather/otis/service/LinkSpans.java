package net.onelitefeather.otis.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.onelitefeather.otis.links.Provider;
import net.onelitefeather.otis.problem.ExternalAccountAlreadyLinkedProblem;
import net.onelitefeather.otis.problem.LinkCodeInvalidProblem;
import net.onelitefeather.otis.problem.LinkCodeRateLimitedProblem;
import net.onelitefeather.otis.problem.LinkNotFoundProblem;
import net.onelitefeather.otis.problem.OtisProblemException;
import net.onelitefeather.otis.problem.PlayerNotFoundProblem;
import net.onelitefeather.otis.problem.ProviderAlreadyLinkedProblem;

import java.util.UUID;

/**
 * Wraps one link use case in an internal span. Records who (player uuid, where known), which provider and how
 * it ended (outcome) - never a link code, external id, display name or link value.
 * <p>
 * Expected outcomes (invalid code, conflict, not found, rate limited, rejected input) leave the span status
 * unset; only unexpected exceptions mark it as error.
 */
final class LinkSpans {

    static final String INSTRUMENTATION_SCOPE = "net.onelitefeather.otis";

    static final AttributeKey<String> PLAYER_UUID = AttributeKey.stringKey("otis.player.uuid");
    static final AttributeKey<String> PROVIDER = AttributeKey.stringKey("otis.link.provider");
    static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("otis.link.outcome");

    static final String ISSUED = "issued";
    static final String RATE_LIMITED = "rate_limited";
    static final String LINKED = "linked";
    static final String UPGRADED = "upgraded";
    static final String INVALID = "invalid";
    static final String CONFLICT = "conflict";
    static final String SET = "set";
    static final String DELETED = "deleted";
    static final String FOUND = "found";
    static final String NOT_FOUND = "not_found";
    static final String REJECTED = "rejected";

    private final Tracer tracer;

    LinkSpans(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
    }

    /** The span of a running use case, as seen by the code inside it. */
    static final class Observation {

        private final Span span;
        private boolean outcomeSet;
        private String outcome;
        private String provider;

        private Observation(Span span) {
            this.span = span;
        }

        void provider(Provider provider) {
            this.provider = provider.wireName();
            span.setAttribute(PROVIDER, this.provider);
        }

        void player(UUID playerUuid) {
            span.setAttribute(PLAYER_UUID, playerUuid.toString());
        }

        void outcome(String outcome) {
            span.setAttribute(OUTCOME, outcome);
            this.outcome = outcome;
            outcomeSet = true;
        }

        String outcome() {
            return outcome;
        }

        String provider() {
            return provider;
        }
    }

    @FunctionalInterface
    interface Body<T> {
        T run(Observation observation);
    }

    /**
     * @param name       span name, e.g. {@code links.redeem}
     * @param playerUuid the player if known up front, otherwise {@code null}
     * @param body       the use case
     */
    <T> T inSpan(String name, UUID playerUuid, Body<T> body) {
        var builder = tracer.spanBuilder(name).setSpanKind(SpanKind.INTERNAL);
        if (playerUuid != null) {
            builder.setAttribute(PLAYER_UUID, playerUuid.toString());
        }
        Span span = builder.startSpan();
        Observation observation = new Observation(span);
        try (Scope ignored = span.makeCurrent()) {
            return body.run(observation);
        } catch (OtisProblemException problem) {
            if (!observation.outcomeSet) {
                observation.outcome(switch (problem) {
                    case PlayerNotFoundProblem _, LinkNotFoundProblem _ -> NOT_FOUND;
                    case LinkCodeInvalidProblem _ -> INVALID;
                    case ExternalAccountAlreadyLinkedProblem _, ProviderAlreadyLinkedProblem _ -> CONFLICT;
                    case LinkCodeRateLimitedProblem _ -> RATE_LIMITED;
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
