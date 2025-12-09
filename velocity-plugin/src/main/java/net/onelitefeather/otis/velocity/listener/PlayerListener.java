package net.onelitefeather.otis.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.onelitefeather.otis.client.api.PlayerApi;
import net.onelitefeather.otis.client.invoker.ApiClient;
import net.onelitefeather.otis.client.invoker.ApiException;
import net.onelitefeather.otis.client.model.AddPlayerRequest;
import net.onelitefeather.otis.client.model.OtisPlayerDTO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.UUID;

/**
 * Listener for player events to create and update player data.
 */
public class PlayerListener {

    private final PlayerApi playerApi;
    private final Logger logger;

    /**
     * Creates a new player listener.
     *
     * @param logger    the logger
     * @param apiClient the API client
     */
    public PlayerListener(@NotNull Logger logger, @NotNull ApiClient apiClient) {
        this.playerApi = new PlayerApi(apiClient);
        this.logger = logger;
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
        try {
            OtisPlayerDTO id = this.playerApi.getPlayerById(uuid);
            updatePlayer(uuid, playerName, id, currentTime);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                // Player does not exist, create new player
                logger.info("Player {} not found, creating new player entry", playerName);
                createPlayer(uuid, playerName, currentTime);
            } else {
                // Other error occurred
                logger.error("Failed to retrieve player data for {}: {}", playerName, e.getMessage());
            }
        }
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
        try {
            OtisPlayerDTO existingPlayer = this.playerApi.getPlayerById(uuid);
            updatePlayer(uuid, playerName, existingPlayer, currentTime);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                logger.warn("Player {} not found during disconnect, no update needed", playerName);
            } else {
                logger.error("Failed to retrieve player data for {} on disconnect: {}", playerName, e.getMessage());
            }
        }
    }

    /**
     * Creates a new player.
     *
     * @param uuid        the player UUID
     * @param playerName  the player name
     * @param currentTime the current time
     */
    private void createPlayer(@NotNull UUID uuid, @NotNull String playerName, long currentTime) {
        OtisPlayerDTO newPlayer = OtisPlayerDTO.builder()
                .firstJoin(currentTime)
                .playerName(playerName)
                .playerUuid(uuid)
                .profileTextures(new HashMap<>())
                .build();
        try {
            this.playerApi.addPlayer(AddPlayerRequest.builder()
                    .playerDTO(newPlayer).build());
        } catch (ApiException e) {
            if (e.getCode() == 500) {
                logger.error("Failed to create player {}: {}", playerName, e.getMessage());
                return;
            }
            logger.error("Unexpected error while creating player {}: {}", playerName, e.getMessage());
        } finally {
            logger.info("Created new player entry for {}", playerName);
        }
    }

    /**
     * Updates an existing player.
     *
     * @param uuid           the player UUID
     * @param playerName     the player name
     * @param existingPlayer the existing player data
     * @param currentTime    the current time
     */
    private void updatePlayer(@NotNull UUID uuid, @NotNull String playerName,
                              @NotNull OtisPlayerDTO existingPlayer, long currentTime) {
        // Create updated player with new last join time
        OtisPlayerDTO updatedPlayer = existingPlayer.toBuilder()
                .lastJoin(currentTime)
                .playerName(playerName)
                .build();
        try {
            this.playerApi.updatePlayer(uuid, AddPlayerRequest.builder()
                    .playerDTO(updatedPlayer).build());
        } catch (ApiException e) {
            if (e.getCode() == 400) {
                logger.error("Failed to update player {}: {}", playerName, e.getMessage());
                return;
            }
            logger.error("Unexpected error while updating player {}: {}", playerName, e.getMessage());
        } finally {
            logger.info("Updated player entry for {}", playerName);
        }

    }
}
