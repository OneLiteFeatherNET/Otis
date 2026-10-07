package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** An unverified link value is neither a valid handle nor an https URL on the provider's domain. */
public final class InvalidLinkValueProblem extends OtisProblemException {

    public InvalidLinkValueProblem() {
        super(HttpStatus.BAD_REQUEST, "invalid-link-value", "Invalid link value",
                "The value must be a handle of the provider or an https URL on the provider's own domain, at most 200 characters.");
    }
}
