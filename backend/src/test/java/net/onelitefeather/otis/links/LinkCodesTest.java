package net.onelitefeather.otis.links;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodesTest {

    private static final Pattern FORMAT = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$");

    private final LinkCodes codes = new LinkCodes(new Random(42));

    @Test
    void generatedCodeHasTwoGroupsOfFourCrockfordCharacters() {
        for (int i = 0; i < 200; i++) {
            String code = codes.generate();
            assertTrue(FORMAT.matcher(code).matches(), "code must match the Crockford format but was " + code);
        }
    }

    @Test
    void generationWithTheSameSeedIsDeterministic() {
        assertEquals(new LinkCodes(new Random(7)).generate(), new LinkCodes(new Random(7)).generate(),
                "same seed, same code");
        assertNotEquals(new LinkCodes(new Random(7)).generate(), new LinkCodes(new Random(8)).generate(),
                "different seed, different code");
    }

    @Test
    void productionRandomSourceProducesValidCodes() {
        String code = new LinkCodes(new SecureRandom()).generate();
        assertTrue(FORMAT.matcher(code).matches(), "secure code must match the format");
    }

    @Test
    void normalizationAcceptsTheCanonicalForm() {
        assertEquals(Optional.of("K7Q4MZ2A"), LinkCodes.normalize("K7Q4-MZ2A"));
    }

    @Test
    void normalizationAcceptsLowercaseInput() {
        assertEquals(Optional.of("K7Q4MZ2A"), LinkCodes.normalize("k7q4-mz2a"));
    }

    @Test
    void normalizationAcceptsAMissingDash() {
        assertEquals(Optional.of("K7Q4MZ2A"), LinkCodes.normalize("k7q4mz2a"));
    }

    @Test
    void normalizationRejectsWrongLength() {
        assertEquals(Optional.empty(), LinkCodes.normalize("K7Q4-MZ2"), "too short");
        assertEquals(Optional.empty(), LinkCodes.normalize("K7Q4-MZ2AA"), "too long");
    }

    @Test
    void normalizationRejectsCharactersOutsideTheAlphabet() {
        assertEquals(Optional.empty(), LinkCodes.normalize("K7Q4-MZ2U"), "U is not in the alphabet");
        assertEquals(Optional.empty(), LinkCodes.normalize("K7Q4-MZ2!"), "punctuation is not in the alphabet");
        assertEquals(Optional.empty(), LinkCodes.normalize(null), "null is not a code");
    }

    @Test
    void hashIsStableAndSixtyFourHexCharacters() {
        String hash = LinkCodes.hash("K7Q4MZ2A");
        assertEquals(hash, LinkCodes.hash("K7Q4MZ2A"), "hash is stable");
        assertTrue(hash.matches("^[0-9a-f]{64}$"), "hash is 64 lowercase hex characters but was " + hash);
        assertNotEquals(hash, LinkCodes.hash("K7Q4MZ2B"), "different codes hash differently");
    }

    @Test
    void hashMatchesTheKnownSha256OfTheNormalizedCode() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", LinkCodes.hash("abc"),
                "SHA-256 of abc");
    }
}
