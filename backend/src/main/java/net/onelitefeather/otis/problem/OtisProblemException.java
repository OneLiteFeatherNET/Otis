package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;
import io.micronaut.problem.HttpStatusType;
import org.zalando.problem.AbstractThrowableProblem;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Base type for domain errors that are reported as RFC 9457 problems with a stable type URI.
 * <p>
 * Throw it (or a subtype) from anywhere in a request; Micronaut Problem JSON's
 * {@code ThrowableProblemHandler} renders it as {@code application/problem+json}.
 * Feature changes add their own subtypes with their own slug.
 */
public class OtisProblemException extends AbstractThrowableProblem {

    /** Common prefix of all problem type URIs; the URIs identify problems and need not resolve. */
    public static final String TYPE_BASE = "https://otis.onelitefeather.net/problems/";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public OtisProblemException(HttpStatus status, String slug, String title, String detail) {
        this(status, slug, title, detail, Map.of());
    }

    public OtisProblemException(HttpStatus status, String slug, String title, String detail,
                                Map<String, Object> extensions) {
        super(typeUri(slug), title, new HttpStatusType(status), detail, null, null, extensions);
    }

    /**
     * Builds the stable type URI for a problem slug.
     *
     * @param slug lowercase kebab-case identifier of the problem; never changes once released
     * @return {@code https://otis.onelitefeather.net/problems/<slug>}
     * @throws IllegalArgumentException if the slug is not lowercase kebab-case
     */
    public static URI typeUri(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Problem slug must be lowercase kebab-case: " + slug);
        }
        return URI.create(TYPE_BASE + slug);
    }
}
