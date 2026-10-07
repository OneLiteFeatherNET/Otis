package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Last resort for exceptions no other handler claims: answers status 500 as a problem without
 * internals and logs the failure once at ERROR. The server span is marked by the Micronaut tracing filter.
 */
@Singleton
public class UnhandledExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnhandledExceptionHandler.class);

    private final ErrorResponseProcessor<?> responseProcessor;

    public UnhandledExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        LOGGER.error("Unhandled request failure", exception);

        ErrorContext context = ErrorContext.builder(request).cause(exception).build();
        return responseProcessor.processResponse(context, HttpResponse.serverError());
    }
}
