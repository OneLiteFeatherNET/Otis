package net.onelitefeather.otis.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.onelitefeather.otis.client.OtisClient;
import net.onelitefeather.otis.client.data.OtisPlayer;
import net.onelitefeather.otis.client.data.OtisPlayerDTO;
import net.onelitefeather.otis.velocity.OtisPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Listener for player events to create and update player data.
 */
public class PlayerListener {

    private final OtisPlugin plugin;
    private final OtisClient client;
    private final Logger logger;

    /**
     * Creates a new player listener.
     *
     * @param plugin the plugin instance
     */
    public PlayerListener(@NotNull OtisPlugin plugin) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.logger = plugin.getLogger();
    }

    /**
     * Handles player login events to create or update player data.
     *
     * @param event the login event
     */
    @Subscribe
    public void onPlayerLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        long currentTime = System.currentTimeMillis();

        // Check if player exists
        client.searchAsyncById(uuid).thenAccept(optionalPlayer -> {
            if (optionalPlayer.isPresent()) {
                // Player exists, update data
                updatePlayer(uuid, playerName, optionalPlayer.get(), currentTime);
            } else {
                // Player doesn't exist, create new player
                createPlayer(uuid, playerName, currentTime);
            }
        }).exceptionally(throwable -> {
            logger.error("Failed to check if player exists", throwable);
            return null;
        });
    }

    /**
     * Handles player disconnect events to update player data.
     *
     * @param event the disconnect event
     */
    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        long currentTime = System.currentTimeMillis();

        // Check if player exists
        client.searchAsyncById(uuid).thenAccept(optionalPlayer -> {
            // Player exists, update last join time
            optionalPlayer.ifPresent(otisPlayer -> updatePlayer(uuid, playerName, otisPlayer, currentTime));
        }).exceptionally(throwable -> {
            logger.error("Failed to update player on disconnect", throwable);
            return null;
        });
    }

    /**
     * Creates a new player.
     *
     * @param uuid       the player UUID
     * @param playerName the player name
     * @param currentTime the current time
     */
    private void createPlayer(@NotNull UUID uuid, @NotNull String playerName, long currentTime) {
        // Create new player with current time as first and last join
        OtisPlayer newPlayer = new OtisPlayerDTO(
                uuid,
                playerName,
                currentTime,
                currentTime,
                new HashMap<>()
        );

        // Add player to database
        client.addAsync(newPlayer).thenAccept(player -> 
            logger.info("Created new player: {}", playerName)
        ).exceptionally(throwable -> {
            logger.error("Failed to create player", throwable);
            return null;
        });
    }

    /**
     * Updates an existing player.
     *
     * @param uuid         the player UUID
     * @param playerName   the player name
     * @param existingPlayer the existing player data
     * @param currentTime  the current time
     */
    private void updatePlayer(@NotNull UUID uuid, @NotNull String playerName, 
                             @NotNull OtisPlayer existingPlayer, long currentTime) {
        // Create updated player with new last join time
        OtisPlayer updatedPlayer = new OtisPlayerDTO(
                uuid,
                playerName,
                existingPlayer.firstJoin(),
                currentTime,
                existingPlayer.profileTextures()
        );

        // Update player in database
        CompletableFuture.runAsync(() -> {
            try {
                client.update(uuid, updatedPlayer);
                logger.info("Updated player: {}", playerName);
            } catch (Exception e) {
                logger.error("Failed to update player", e);
            }
        });
    }
}