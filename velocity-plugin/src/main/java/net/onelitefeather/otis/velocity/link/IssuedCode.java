package net.onelitefeather.otis.velocity.link;

import java.time.Instant;

/**
 * A link code issued by Otis.
 *
 * @param code      the code, formatted as {@code XXXX-XXXX}
 * @param expiresAt the instant after which the code is no longer valid
 */
public record IssuedCode(String code, Instant expiresAt) {
}
