package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** A setting key that is not valid Adventure key syntax. */
public final class InvalidSettingKeyProblem extends OtisProblemException {

    public InvalidSettingKeyProblem(String key) {
        super(HttpStatus.BAD_REQUEST, "invalid-setting-key", "Invalid setting key",
                "Setting key '" + key + "' is not valid; the namespace allows [a-z0-9_.-], the value [a-z0-9_./-].");
    }
}
