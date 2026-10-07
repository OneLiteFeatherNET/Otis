package net.onelitefeather.otis.settings;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.problem.InvalidSettingKeyProblem;
import net.onelitefeather.otis.problem.MissingNamespaceProblem;
import net.onelitefeather.otis.problem.ReservedNamespaceProblem;

/**
 * Parses setting keys and applies the namespace rules of Otis in one place.
 * <p>
 * Syntax validation is Adventure's own ({@link Key#key(String, String)}); this class only adds what
 * Adventure does not: {@code Key.key("x")} silently means {@code minecraft:x}, which Otis rejects.
 */
public final class SettingKeys {

    /** Namespace of the game itself; never usable for settings. */
    public static final String RESERVED_NAMESPACE = "minecraft";

    private SettingKeys() {
    }

    /**
     * @param raw the key as {@code namespace:value}
     * @return the parsed key
     * @throws MissingNamespaceProblem   if {@code raw} has no (or an empty) namespace
     * @throws ReservedNamespaceProblem  if the namespace is {@code minecraft}
     * @throws InvalidSettingKeyProblem  if {@code raw} is not valid Adventure key syntax
     */
    public static Key parse(String raw) {
        if (raw == null) {
            throw new MissingNamespaceProblem("null");
        }
        int separator = raw.indexOf(Key.DEFAULT_SEPARATOR);
        if (separator <= 0) {
            throw new MissingNamespaceProblem(raw);
        }
        if (separator == raw.length() - 1) {
            // Adventure accepts an empty value, but a setting without a name is useless
            throw new InvalidSettingKeyProblem(raw);
        }
        Key key;
        try {
            key = Key.key(raw.substring(0, separator), raw.substring(separator + 1));
        } catch (InvalidKeyException exception) {
            throw new InvalidSettingKeyProblem(raw);
        }
        if (RESERVED_NAMESPACE.equals(key.namespace())) {
            throw new ReservedNamespaceProblem(raw);
        }
        return key;
    }
}
