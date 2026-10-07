package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** A setting key in the {@code minecraft} namespace, which belongs to the game itself. */
public final class ReservedNamespaceProblem extends OtisProblemException {

    public ReservedNamespaceProblem(String key) {
        super(HttpStatus.BAD_REQUEST, "reserved-namespace", "Reserved key namespace",
                "Setting key '" + key + "' uses the reserved namespace 'minecraft'; "
                        + "use 'olf' for generic settings or the namespace of your game.");
    }
}
