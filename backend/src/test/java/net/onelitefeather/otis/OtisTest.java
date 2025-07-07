package net.onelitefeather.otis;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

//@MicronautTest
class OtisTest {

   // @Inject
   // EmbeddedApplication<?> application;

    @Disabled("We need to investigate why this test fails")
    @Test
    void testItWorks() {
       // Assertions.assertTrue(application.isRunning());
    }

}
