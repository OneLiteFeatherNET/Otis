package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** A request body that is not valid JSON. */
public final class InvalidSettingValueProblem extends OtisProblemException {

    public InvalidSettingValueProblem() {
        super(HttpStatus.BAD_REQUEST, "invalid-setting-value", "Invalid setting value",
                "The request body is not valid JSON.");
    }
}
