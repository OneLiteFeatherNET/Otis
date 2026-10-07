package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** A setting key without an explicit namespace; {@code minecraft:} is never assumed. */
public final class MissingNamespaceProblem extends OtisProblemException {

    public MissingNamespaceProblem(String key) {
        super(HttpStatus.BAD_REQUEST, "missing-namespace", "Missing key namespace",
                "Setting key '" + key + "' has no namespace; use the form namespace:value, e.g. lobby:player_hider.");
    }
}
