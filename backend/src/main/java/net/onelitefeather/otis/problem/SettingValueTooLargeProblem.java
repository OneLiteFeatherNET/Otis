package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** A setting value whose serialized form exceeds the maximum size. */
public final class SettingValueTooLargeProblem extends OtisProblemException {

    public SettingValueTooLargeProblem(int maxBytes) {
        super(HttpStatus.REQUEST_ENTITY_TOO_LARGE, "setting-value-too-large", "Setting value too large",
                "The serialized setting value exceeds " + maxBytes + " bytes.");
    }
}
