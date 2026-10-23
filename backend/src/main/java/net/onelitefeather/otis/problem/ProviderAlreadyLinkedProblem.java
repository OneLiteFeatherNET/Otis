package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** The player already has a verified link for the provider; it has to be removed first. */
public final class ProviderAlreadyLinkedProblem extends OtisProblemException {

    public ProviderAlreadyLinkedProblem() {
        super(HttpStatus.CONFLICT, "provider-already-linked", "Provider already linked",
                "The player already has a verified link for this provider. Unlink it first.");
    }
}
