package net.onelitefeather.otis.api;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

/**
 * Test-only endpoints that fail on purpose, so error handling can be exercised end to end.
 */
@Requires(env = "test")
@Controller("/test-failures")
class FailingEndpointsController {

    static final String SECRET_MESSAGE = "secret-internal-detail";

    @Get("/unexpected")
    String unexpected() {
        throw new IllegalStateException(SECRET_MESSAGE);
    }
}
