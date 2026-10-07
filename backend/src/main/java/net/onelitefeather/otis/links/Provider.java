package net.onelitefeather.otis.links;

import net.onelitefeather.otis.problem.InvalidLinkValueProblem;
import net.onelitefeather.otis.problem.UnsupportedProviderProblem;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The external account providers a player can link, with the rules for unverified (public) link values.
 * A new provider is a new constant; nothing else changes.
 */
public enum Provider {

    DISCORD("^[A-Za-z0-9_.]{2,32}$"),
    TWITCH("^[A-Za-z0-9_]{4,25}$", "twitch.tv"),
    YOUTUBE("^@?[A-Za-z0-9_.-]{3,30}$", "youtube.com", "youtu.be"),
    X("^@?[A-Za-z0-9_]{1,15}$", "x.com", "twitter.com"),
    TIKTOK("^@?[A-Za-z0-9_.]{2,24}$", "tiktok.com"),
    GITHUB("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$", "github.com");

    /** Longest accepted unverified value in characters. */
    public static final int MAX_VALUE_LENGTH = 200;

    private final Pattern handle;
    private final List<String> hosts;

    Provider(String handle, String... hosts) {
        this.handle = Pattern.compile(handle);
        this.hosts = List.of(hosts);
    }

    /** @return the lowercase name used in paths and bodies */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param wireName the provider as sent by a client
     * @return the provider
     * @throws UnsupportedProviderProblem if no provider has this exact lowercase name
     */
    public static Provider parse(String wireName) {
        if (wireName != null) {
            for (Provider provider : values()) {
                if (provider.wireName().equals(wireName)) {
                    return provider;
                }
            }
        }
        throw new UnsupportedProviderProblem();
    }

    /**
     * @param value a handle or an https URL on the provider's own domain
     * @return the value, unchanged
     * @throws InvalidLinkValueProblem if the value is neither a valid handle nor a valid URL
     */
    public String validate(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
            throw new InvalidLinkValueProblem();
        }
        if (handle.matcher(value).matches() || isOwnUrl(value)) {
            return value;
        }
        throw new InvalidLinkValueProblem();
    }

    private boolean isOwnUrl(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException notAUri) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return hosts.stream().anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }
}
