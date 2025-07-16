package net.onelitefeather.otis.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import net.onelitefeather.otis.client.OtisClient;
import org.jetbrains.annotations.NotNull;

public class PlayerDisconnectListener {

    private final OtisClient otisClient;

    public PlayerDisconnectListener(@NotNull OtisClient otisClient) {
        this.otisClient = otisClient;
    }

    @Subscribe
    public void onDisconnect(@NotNull DisconnectEvent event) {

    }
}
