package net.onelitefeather.otis.links;

import net.onelitefeather.otis.problem.InvalidLinkValueProblem;
import net.onelitefeather.otis.problem.UnsupportedProviderProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderTest {

    @Test
    void wireNamesAreLowercase() {
        for (Provider provider : Provider.values()) {
            assertEquals(provider.name().toLowerCase(java.util.Locale.ROOT), provider.wireName(), "wire name of " + provider);
        }
        assertEquals(6, Provider.values().length, "the supported providers");
    }

    @ParameterizedTest
    @ValueSource(strings = {"discord", "twitch", "youtube", "x", "tiktok", "github"})
    void parsesEverySupportedWireName(String wire) {
        assertEquals(wire, Provider.parse(wire).wireName(), "round trip of " + wire);
    }

    @ParameterizedTest
    @ValueSource(strings = {"myspace", "Discord", "DISCORD", "", " discord"})
    void rejectsUnsupportedProvidersWithAProblem(String wire) {
        assertThrows(UnsupportedProviderProblem.class, () -> Provider.parse(wire), "provider " + wire);
    }

    @Test
    void rejectsANullProvider() {
        assertThrows(UnsupportedProviderProblem.class, () -> Provider.parse(null), "null provider");
    }

    @ParameterizedTest
    @CsvSource({
            "discord, meinerlp",
            "discord, mei.ner_lp",
            "twitch, onelitefeather",
            "youtube, @onelitefeather",
            "youtube, onelitefeather",
            "x, onelitefeather",
            "x, @onelitefeather",
            "tiktok, @onelitefeather",
            "github, onelitefeather",
            "twitch, https://www.twitch.tv/onelitefeather",
            "twitch, https://twitch.tv/onelitefeather",
            "youtube, https://www.youtube.com/@onelitefeather",
            "youtube, https://youtu.be/abc123",
            "x, https://x.com/onelitefeather",
            "x, https://twitter.com/onelitefeather",
            "tiktok, https://www.tiktok.com/@onelitefeather",
            "github, https://github.com/OneLiteFeather",
    })
    void acceptsValidHandlesAndUrls(String provider, String value) {
        assertEquals(value, Provider.parse(provider).validate(value), "valid value is kept as given");
    }

    @ParameterizedTest
    @CsvSource({
            "discord, https://discord.com/users/1",
            "discord, a",
            "discord, has space",
            "twitch, https://evil.example/twitch",
            "twitch, http://www.twitch.tv/onelitefeather",
            "twitch, https://twitch.tv.evil.example/x",
            "twitch, https://eviltwitch.tv/x",
            "twitch, https://user@twitch.tv/x",
            "twitch, ftp://twitch.tv/x",
            "youtube, https://youtube.com.evil.example/",
            "x, toolonghandle_over_fifteen",
            "tiktok, https://tiktok.com/has space",
            "github, -leadingdash",
            "github, https://gist.example/x",
    })
    void rejectsInvalidHandlesAndUrls(String provider, String value) {
        Provider parsed = Provider.parse(provider);
        assertThrows(InvalidLinkValueProblem.class, () -> parsed.validate(value), provider + " must reject " + value);
    }

    @Test
    void rejectsMoreThanTwoHundredCharacters() {
        String longUrl = "https://www.twitch.tv/" + "a".repeat(200);
        assertThrows(InvalidLinkValueProblem.class, () -> Provider.TWITCH.validate(longUrl), "over 200 characters");
        String exactly200 = "https://www.twitch.tv/" + "a".repeat(200 - "https://www.twitch.tv/".length());
        assertEquals(exactly200, Provider.TWITCH.validate(exactly200), "exactly 200 characters is accepted");
    }

    @Test
    void rejectsNullAndBlankValues() {
        assertThrows(InvalidLinkValueProblem.class, () -> Provider.GITHUB.validate(null), "null");
        assertThrows(InvalidLinkValueProblem.class, () -> Provider.GITHUB.validate(" "), "blank");
    }
}
