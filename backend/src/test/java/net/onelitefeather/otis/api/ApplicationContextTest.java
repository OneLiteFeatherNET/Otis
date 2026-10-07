package net.onelitefeather.otis.api;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = "test", transactional = false)
class ApplicationContextTest {

    @Inject
    EmbeddedApplication<?> application;

    @Test
    void contextStartsAgainstInMemoryDatabase() {
        assertTrue(application.isRunning(), "application must start with the H2 test configuration");
    }
}
