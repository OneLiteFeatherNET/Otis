package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** No verified link exists for the requested external account. */
public final class LinkNotFoundProblem extends OtisProblemException {

    public LinkNotFoundProblem() {
        super(HttpStatus.NOT_FOUND, "link-not-found", "Link not found",
                "No verified link exists for this external account.");
    }
}
