package net.onelitefeather.otis.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.onelitefeather.otis.client.invoker.ApiException;
import net.onelitefeather.otis.client.invoker.JSON;
import net.onelitefeather.otis.client.model.ProblemDetail;

import java.util.Locale;
import java.util.Optional;

/**
 * Reads the RFC 9457 problem details the Otis API sends with every error response, so callers do
 * not have to parse error bodies themselves.
 *
 * <pre>{@code
 * try {
 *     playerApi.getPlayerById(uuid);
 * } catch (ApiException e) {
 *     ProblemDetails.from(e).ifPresent(problem -> log.warn("Otis said {}: {}", problem.getType(), problem.getDetail()));
 * }
 * }</pre>
 */
public final class ProblemDetails {

    private static final String PROBLEM_JSON = "application/problem+json";

    private ProblemDetails() {
    }

    /**
     * Extracts the problem details from a failed API call.
     *
     * @param exception the exception thrown by a client method
     * @return the problem, or empty when the error response is not a Problem Details document
     * (for example an error page from a proxy); never throws
     */
    public static Optional<ProblemDetail> from(ApiException exception) {
        if (!hasProblemContentType(exception)) {
            return Optional.empty();
        }
        String body = exception.getResponseBody();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            ProblemDetail problem = JSON.getDefault().getMapper().readValue(body, ProblemDetail.class);
            return Optional.ofNullable(problem);
        } catch (JsonProcessingException _) {
            return Optional.empty();
        }
    }

    private static boolean hasProblemContentType(ApiException exception) {
        return exception.getResponseHeaders() != null
                && exception.getResponseHeaders().firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .filter(value -> value.equals(PROBLEM_JSON) || value.startsWith(PROBLEM_JSON + ";"))
                .isPresent();
    }
}
