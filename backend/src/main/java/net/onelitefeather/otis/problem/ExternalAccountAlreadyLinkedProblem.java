package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** The external account is already linked to another player. */
public final class ExternalAccountAlreadyLinkedProblem extends OtisProblemException {

    public ExternalAccountAlreadyLinkedProblem() {
        super(HttpStatus.CONFLICT, "external-account-already-linked", "External account already linked",
                "This external account is already linked to a player.");
    }
}
