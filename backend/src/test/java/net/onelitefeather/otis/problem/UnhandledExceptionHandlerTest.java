package net.onelitefeather.otis.problem;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.problem.conf.ProblemConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.zalando.problem.Problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class UnhandledExceptionHandlerTest {

    private static final String SECRET = "secret-internal-detail";

    private final OtisProblemBodyProvider provider = new OtisProblemBodyProvider(new ProblemConfiguration() {
        @Override
        public boolean isStackTrace() {
            return false;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }, "otlp");

    private final UnhandledExceptionHandler handler =
            new UnhandledExceptionHandler((context, response) -> response.body(provider.body(context, response)));

    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(UnhandledExceptionHandler.class);

    @BeforeEach
    void captureLogs() {
        logEvents.start();
        handlerLogger.addAppender(logEvents);
    }

    @AfterEach
    void releaseLogs() {
        handlerLogger.detachAppender(logEvents);
        logEvents.stop();
    }

    private HttpResponse<?> handleFailure(Throwable failure) {
        return handler.handle(HttpRequest.GET("/boom"), failure);
    }

    @Test
    void answersStatus500() {
        HttpResponse<?> response = handleFailure(new IllegalStateException(SECRET));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus(), "status");
    }

    @Test
    void problemBodyDoesNotExposeTheExceptionMessage() {
        HttpResponse<?> response = handleFailure(new IllegalStateException(SECRET));

        Problem problem = (Problem) response.body();
        assertEquals(500, problem.getStatus().getStatusCode(), "problem status");
        assertEquals("Internal Server Error", problem.getTitle(), "problem title");
        assertNull(problem.getDetail(), "detail must not carry the exception message");
        assertFalse(problem.toString().contains(SECRET), "exception message must not leak");
    }

    @Test
    void logsOneErrorWithTheExceptionAttached() {
        IllegalStateException failure = new IllegalStateException(SECRET);

        handleFailure(failure);

        assertEquals(1, logEvents.list.size(), "exactly one log event expected");
        ILoggingEvent event = logEvents.list.getFirst();
        assertEquals(Level.ERROR, event.getLevel(), "log level");
        assertSame(failure, ((ch.qos.logback.classic.spi.ThrowableProxy) event.getThrowableProxy()).getThrowable(),
                "the exception must be the last argument");
        assertFalse(event.getFormattedMessage().contains(SECRET), "message text must not repeat exception details");
    }
}
