package net.onelitefeather.otis.problem;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.problem.HttpStatusType;
import io.micronaut.problem.violations.ConstraintViolationThrowableProblem;
import io.micronaut.problem.violations.ProblemConstraintViolationExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;

/**
 * Reports bean validation failures as status 400 with the Otis {@code constraint-violation}
 * problem type and a {@code violations} member listing {@code field} and {@code message}.
 * <p>
 * Same behaviour as the Micronaut Problem JSON handler it replaces, except for the type URI.
 */
@Singleton
@Replaces(ProblemConstraintViolationExceptionHandler.class)
public class OtisConstraintViolationHandler extends ProblemConstraintViolationExceptionHandler {

    /** Slug of the {@code constraint-violation} problem type. */
    public static final String SLUG = "constraint-violation";

    private final ErrorResponseProcessor<?> responseProcessor;

    public OtisConstraintViolationHandler(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
        this.responseProcessor = responseProcessor;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConstraintViolationException exception) {
        var violations = exception.getConstraintViolations().stream()
                .map(this::createViolation)
                .toList();
        var problem = new ConstraintViolationThrowableProblem(
                OtisProblemException.typeUri(SLUG), new HttpStatusType(HttpStatus.BAD_REQUEST), violations);
        ErrorContext context = ErrorContext.builder(request)
                .cause(problem)
                .errorMessage(exception.getMessage())
                .build();
        return responseProcessor.processResponse(context, HttpResponse.status(HttpStatus.BAD_REQUEST));
    }
}
