package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtisProblemExceptionTest {

    @Test
    void typeIsStableUriUnderOtisBaseAndSlug() {
        var problem = new OtisProblemException(HttpStatus.CONFLICT, "sample-conflict", "Sample conflict", "Detail.");

        assertEquals(URI.create("https://otis.onelitefeather.net/problems/sample-conflict"), problem.getType(),
                "type must be the base URI followed by the slug");
    }

    @Test
    void statusTitleAndDetailAreCarried() {
        var problem = new OtisProblemException(HttpStatus.CONFLICT, "sample-conflict", "Sample conflict", "Detail.");

        assertEquals(409, problem.getStatus().getStatusCode(), "status code");
        assertEquals("Sample conflict", problem.getTitle(), "title");
        assertEquals("Detail.", problem.getDetail(), "detail");
    }

    @Test
    void extensionMembersAreExposedAsParameters() {
        var problem = new OtisProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY, "bad-key", "Bad key", "Key is invalid.", Map.of("key", "a.b"));

        assertEquals(Map.of("key", "a.b"), problem.getParameters(), "extension members");
    }

    @Test
    void withoutExtensionsParametersAreEmpty() {
        var problem = new OtisProblemException(HttpStatus.CONFLICT, "sample-conflict", "Sample conflict", "Detail.");

        assertTrue(problem.getParameters().isEmpty(), "no extension members expected");
    }

    @Test
    void slugMustBeLowercaseKebabCase() {
        assertThrows(IllegalArgumentException.class,
                () -> new OtisProblemException(HttpStatus.CONFLICT, "Not_Kebab", "t", "d"),
                "uppercase and underscores are not allowed in a slug");
    }

    @Test
    void typeUriFactoryRejectsBlankSlug() {
        assertThrows(IllegalArgumentException.class, () -> OtisProblemException.typeUri(" "),
                "blank slug must be rejected");
    }
}
