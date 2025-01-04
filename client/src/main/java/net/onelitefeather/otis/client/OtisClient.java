package net.onelitefeather.otis.client;

import net.onelitefeather.otis.client.data.OtisPlayer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface OtisClient {

    /**
     * Create a new OtisWebClient with the given URL.
     *
     * @param url the URL of the server
     * @return the new OtisWebClient instance
     */
    @Contract(value = "_ -> new", pure = true)
    static @NotNull OtisClient newClient(@NotNull String url) {
        return new OtisWebClient(url, Duration.ofSeconds(10));
    }

    /**
     * Create a new OtisWebClient with the given URL and timeout.
     *
     * @param url     the URL of the server
     * @param timeout the timeout for the client
     * @return the new OtisWebClient instance
     */
    @Contract(value = "_, _ -> new", pure = true)
    static @NotNull OtisClient newClient(@NotNull String url, @NotNull Duration timeout) {
        return new OtisWebClient(url, timeout);
    }

    /**
     * Add a new player to the database.
     *
     * @param player the player to add
     * @return the added player
     */
    @NotNull CompletableFuture<OtisPlayer> addAsync(@NotNull OtisPlayer player);

    /**
     * Get a player by the given owner.
     *
     * @param owner the owner of the player
     * @return the player if found
     */
    @NotNull Optional<OtisPlayer> get(@NotNull UUID owner);

    /**
     * Update the player with the given owner.
     *
     * @param owner  the owner of the player
     * @param player the player to update
     * @return the updated player
     */
    @NotNull OtisPlayer update(@NotNull UUID owner, @NotNull OtisPlayer player);

    /**
     * Delete the player with the given owner.
     *
     * @param owner the owner of the player
     * @return the deleted player
     */
    @NotNull CompletableFuture<OtisPlayer> deleteAsync(@NotNull UUID owner);

    /**
     * Search for a player by the given name.
     *
     * @param name the name of the player
     * @return the player if found
     */
    @NotNull CompletableFuture<Optional<OtisPlayer>> searchAsyncByName(@NotNull String name);

    /**
     * Search for a player by the given id.
     *
     * @param owner the id of the player
     * @return the player if found
     */
    @NotNull CompletableFuture<Optional<OtisPlayer>> searchAsyncById(@NotNull UUID owner);

    /**
     * Get all players from the database.
     *
     * @param page   the page to get
     * @param number the number of players to get
     * @return a list of all players
     */
    @NotNull List<OtisPlayer> getAll(int page, int number);
}
