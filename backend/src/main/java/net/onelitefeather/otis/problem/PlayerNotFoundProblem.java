package net.onelitefeather.otis.problem;

import io.micronaut.http.HttpStatus;

/** No player with the given Mojang uuid is stored in Otis; players are never created implicitly. */
public final class PlayerNotFoundProblem extends OtisProblemException {

    public PlayerNotFoundProblem() {
        super(HttpStatus.NOT_FOUND, "player-not-found", "Player not found",
                "No player with the given uuid is known to Otis.");
    }
}
