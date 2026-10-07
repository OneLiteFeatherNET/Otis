package net.onelitefeather.otis.settings;

import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.problem.InvalidSettingKeyProblem;
import net.onelitefeather.otis.problem.MissingNamespaceProblem;
import net.onelitefeather.otis.problem.ReservedNamespaceProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {"player_hider", "", "language"})
    void keyWithoutSeparatorIsMissingTheNamespace(String raw) {
        assertThrows(MissingNamespaceProblem.class, () -> SettingKeys.parse(raw),
                "'" + raw + "' has no namespace and must not default to minecraft");
    }

    @ParameterizedTest
    @ValueSource(strings = {":player_hider", ":"})
    void keyWithEmptyNamespaceIsMissingTheNamespace(String raw) {
        assertThrows(MissingNamespaceProblem.class, () -> SettingKeys.parse(raw),
                "'" + raw + "' has an empty namespace");
    }

    @Test
    void minecraftNamespaceIsReserved() {
        assertThrows(ReservedNamespaceProblem.class, () -> SettingKeys.parse("minecraft:player_hider"),
                "the minecraft namespace belongs to the game");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Lobby:Player Hider", "lobby:Player", "LOBBY:player", "lobby:a b", "lobby:", "a:b:c", "lobby:ü"})
    void keyWithInvalidCharactersIsInvalid(String raw) {
        assertThrows(InvalidSettingKeyProblem.class, () -> SettingKeys.parse(raw),
                "'" + raw + "' violates the Adventure key syntax");
    }

    @Test
    void nullKeyIsMissingTheNamespace() {
        assertThrows(MissingNamespaceProblem.class, () -> SettingKeys.parse(null), "null is not a key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"olf:language", "lobby:player_hider", "bed-wars.x:shop/layout_1"})
    void validKeysAreParsed(String raw) {
        Key key = SettingKeys.parse(raw);

        assertEquals(raw, key.asString(), "round trip of " + raw);
    }

    @Test
    void generalNamespaceIsAccepted() {
        assertEquals("olf", SettingKeys.parse("olf:language").namespace(), "olf is the generic namespace");
    }
}
