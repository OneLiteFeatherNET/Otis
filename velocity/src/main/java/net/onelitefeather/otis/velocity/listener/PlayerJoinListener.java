package net.onelitefeather.otis.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.onelitefeather.otis.client.OtisClient;
import net.onelitefeather.otis.client.data.OtisPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlayerJoinListener {

    private final OtisClient otisClient;

    public PlayerJoinListener(@NotNull OtisClient otisClient) {
        this.otisClient = otisClient;
    }

    @Subscribe
    public void onJoin(@NotNull PostLoginEvent event) {
        Player player = event.getPlayer();

        CompletableFuture<Optional<OtisPlayer>> optionalCompletableFuture = otisClient.searchAsyncById(player.getUniqueId());
    }
}
