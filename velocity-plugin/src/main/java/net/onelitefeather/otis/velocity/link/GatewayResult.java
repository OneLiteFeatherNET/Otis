package net.onelitefeather.otis.velocity.link;

/**
 * Outcome of a call to Otis.
 *
 * @param <T> the type of a successful result
 */
public sealed interface GatewayResult<T> {

    /**
     * The call succeeded.
     *
     * @param value the result
     * @param <T>   the result type
     */
    record Success<T>(T value) implements GatewayResult<T> {
    }

    /**
     * Otis rejected the call with a problem.
     *
     * @param slug the last segment of the problem type, for example {@code link-code-rate-limited}
     * @param <T>  the result type
     */
    record Problem<T>(String slug) implements GatewayResult<T> {
    }

    /**
     * Otis could not be reached or failed with a server error.
     *
     * @param cause a short, non-personal description of the failure (for example the exception type)
     * @param <T>   the result type
     */
    record Unavailable<T>(String cause) implements GatewayResult<T> {
    }
}
