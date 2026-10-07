package net.onelitefeather.otis.problem;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.problem.ProblemJsonErrorResponseBodyProvider;
import io.micronaut.problem.conf.ProblemConfiguration;
import io.micronaut.problem.violations.ConstraintViolationThrowableProblem;
import jakarta.inject.Singleton;
import org.zalando.problem.Problem;
import org.zalando.problem.ProblemBuilder;
import org.zalando.problem.ThrowableProblem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the problem body for every error response. Extends the Micronaut Problem JSON provider so
 * that generic HTTP errors without a specific title get the reason phrase of their status code, as
 * RFC 9457 recommends for the {@code about:blank} type.
 */
@Singleton
@Replaces(ProblemJsonErrorResponseBodyProvider.class)
public class OtisProblemBodyProvider extends ProblemJsonErrorResponseBodyProvider {

    public OtisProblemBodyProvider(ProblemConfiguration configuration) {
        super(configuration);
    }

    @Override
    public Problem body(ErrorContext errorContext, HttpResponse<?> response) {
        ThrowableProblem problem = errorContext.getRootCause()
                .filter(ThrowableProblem.class::isInstance)
                .map(ThrowableProblem.class::cast)
                .orElseGet(() -> defaultProblem(errorContext, response.getStatus()));
        Map<String, Object> extensions = new LinkedHashMap<>(problem.getParameters());
        if (problem instanceof ConstraintViolationThrowableProblem violations) {
            extensions.put("violations", violations.getViolations());
        }
        return new ProblemResponse(problem, extensions);
    }

    @Override
    protected ThrowableProblem defaultProblem(ErrorContext errorContext, HttpStatus status) {
        ThrowableProblem base = super.defaultProblem(errorContext, status);
        if (base.getTitle() != null) {
            return base;
        }
        ProblemBuilder builder = Problem.builder()
                .withType(base.getType())
                .withTitle(status.getReason())
                .withStatus(base.getStatus())
                .withDetail(base.getDetail())
                .withInstance(base.getInstance());
        base.getParameters().forEach(builder::with);
        return builder.build();
    }
}
