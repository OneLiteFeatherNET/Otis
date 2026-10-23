package net.onelitefeather.otis.links;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Generation, normalization and hashing of link codes, built on the JDK only.
 * <p>
 * A code is 40 random bits encoded as 8 Crockford Base32 characters, shown as {@code XXXX-XXXX}. Only the
 * SHA-256 hash of the normalized code (uppercase, without the dash) is ever stored. The random source is
 * injected: production passes a {@link java.security.SecureRandom}, tests a seeded {@link Random}.
 */
public final class LinkCodes {

    /** Crockford Base32: digits and uppercase letters without I, L, O and U. */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 8;
    private static final int BITS_PER_CHARACTER = 5;

    private final Random random;

    public LinkCodes(Random random) {
        this.random = random;
    }

    /** @return a new code in display form, e.g. {@code K7Q4-MZ2A} */
    public String generate() {
        long bits = random.nextLong();
        char[] characters = new char[LENGTH];
        for (int i = LENGTH - 1; i >= 0; i--) {
            characters[i] = ALPHABET.charAt((int) (bits & 0x1F));
            bits >>>= BITS_PER_CHARACTER;
        }
        return display(new String(characters));
    }

    /**
     * @param input the code as typed by a user
     * @return the normalized code (8 uppercase alphabet characters), or empty if the input cannot be a code
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.strip().replace("-", "").toUpperCase(Locale.ROOT);
        if (normalized.length() != LENGTH) {
            return Optional.empty();
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (ALPHABET.indexOf(normalized.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(normalized);
    }

    /** @return the display form {@code XXXX-XXXX} of a normalized code */
    public static String display(String normalized) {
        return normalized.substring(0, 4) + "-" + normalized.substring(4);
    }

    /**
     * @param normalized a normalized code
     * @return the SHA-256 of the code as 64 lowercase hex characters
     */
    public static String hash(String normalized) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}
