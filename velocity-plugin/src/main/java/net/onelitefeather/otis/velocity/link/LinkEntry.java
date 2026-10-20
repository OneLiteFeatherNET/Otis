package net.onelitefeather.otis.velocity.link;

/**
 * One link of a player.
 *
 * @param provider the provider id
 * @param label    the displayed value, display name or external id
 * @param verified whether the link was verified with a code
 */
public record LinkEntry(String provider, String label, boolean verified) {
}
