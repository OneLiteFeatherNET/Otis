package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** The link code is unknown, expired, used or issued for another provider; the cases are deliberately not told apart. */
public final class LinkCodeInvalidProblem extends OtisProblemException {

    public LinkCodeInvalidProblem() {
        super(HttpStatus.GONE, "link-code-invalid", "Link code invalid",
                "The link code is invalid or has expired.");
    }
}
