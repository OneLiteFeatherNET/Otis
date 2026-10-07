package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** The player requested too many link codes recently. */
public final class LinkCodeRateLimitedProblem extends OtisProblemException {

    public LinkCodeRateLimitedProblem() {
        super(HttpStatus.TOO_MANY_REQUESTS, "link-code-rate-limited", "Too many link codes",
                "At most 5 link codes can be issued per player within 60 minutes.");
    }
}
