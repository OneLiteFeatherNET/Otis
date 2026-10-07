package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** The player has no setting for the requested key. */
public final class SettingNotFoundProblem extends OtisProblemException {

    public SettingNotFoundProblem() {
        super(HttpStatus.NOT_FOUND, "setting-not-found", "Setting not found",
                "The player has no setting for the requested key.");
    }
}
