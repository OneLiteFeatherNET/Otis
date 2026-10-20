package net.onelitefeather.otis.velocity.link;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The account providers Otis supports, identified by their lowercase names in commands and in the API.
 */
public enum LinkProvider {
    DISCORD(true),
    TWITCH(true),
    YOUTUBE(true),
    X(false),
    TIKTOK(false),
    GITHUB(false);

    private final boolean codeLinkable;

    LinkProvider(boolean codeLinkable) {
        this.codeLinkable = codeLinkable;
    }

    /**
     * @return the lowercase name used in commands and in the API
     */
    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return whether the provider can be verified with a link code (otherwise only {@code /social} applies)
     */
    public boolean codeLinkable() {
        return codeLinkable;
    }

    /**
     * @param input the provider as typed by a player
     * @return the matching provider, or empty if unsupported
     */
    public static @NotNull Optional<LinkProvider> parse(@NotNull String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(provider -> provider.id().equals(normalized)).findFirst();
    }

    /**
     * @return the ids of all providers
     */
    public static @NotNull List<String> allIds() {
        return Arrays.stream(values()).map(LinkProvider::id).toList();
    }

    /**
     * @return the ids of the providers that can be linked with a code
     */
    public static @NotNull List<String> codeLinkableIds() {
        return Arrays.stream(values()).filter(LinkProvider::codeLinkable).map(LinkProvider::id).toList();
    }
}
