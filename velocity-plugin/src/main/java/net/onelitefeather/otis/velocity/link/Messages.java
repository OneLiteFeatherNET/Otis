package net.onelitefeather.otis.velocity.link;

/**
 * Translation keys of all player-facing link messages. Every key must exist in the fallback bundle
 * ({@code lang/otis_en.properties}) and in every shipped bundle.
 */
public final class Messages {

    public static final String CODE_ISSUED = "otis.link.code.issued";
    public static final String CODE_COPY_HOVER = "otis.link.code.copy-hover";
    public static final String UNSUPPORTED_FOR_LINK = "otis.link.provider.unsupported-for-link";
    public static final String LIST_HEADER = "otis.link.list.header";
    public static final String LIST_ENTRY_VERIFIED = "otis.link.list.entry.verified";
    public static final String LIST_ENTRY_UNVERIFIED = "otis.link.list.entry.unverified";
    public static final String LIST_EMPTY = "otis.link.list.empty";
    public static final String UNLINKED = "otis.link.unlinked";
    public static final String SOCIAL_SET = "otis.link.social.set";
    public static final String ERROR_RATE_LIMITED = "otis.link.error.rate-limited";
    public static final String ERROR_INVALID_VALUE = "otis.link.error.invalid-value";
    public static final String ERROR_ALREADY_VERIFIED = "otis.link.error.already-verified";
    public static final String ERROR_PLAYER_UNKNOWN = "otis.link.error.player-unknown";
    public static final String ERROR_UNSUPPORTED_PROVIDER = "otis.link.error.unsupported-provider";
    public static final String ERROR_UNAVAILABLE = "otis.link.error.unavailable";
    public static final String ERROR_PLAYERS_ONLY = "otis.link.error.players-only";

    private Messages() {
    }
}
