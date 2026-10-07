package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** The provider is not one of the supported account providers. */
public final class UnsupportedProviderProblem extends OtisProblemException {

    public UnsupportedProviderProblem() {
        super(HttpStatus.BAD_REQUEST, "unsupported-provider", "Unsupported provider",
                "The provider must be one of discord, twitch, youtube, x, tiktok or github.");
    }
}
