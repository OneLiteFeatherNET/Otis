package net.onelitefeather.otis.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.onelitefeather.otis.client.OtisClient;
import net.onelitefeather.otis.velocity.listener.PlayerDisconnectListener;
import net.onelitefeather.otis.velocity.listener.PlayerJoinListener;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(
        id = "otis-velocity",
        name = "Otis Velocity",
        version = "0.1.0-SNAPSHOT",
        authors = {"OneLiteFeather"}
)
public class OtisVelocity {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    /**
     * Initializes the plugin and registers the necessary components.
     */
    public OtisVelocity(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(@NotNull ProxyInitializeEvent event) {
        proxyServer.getEventManager().register(this, new PlayerDisconnectListener(null));
        proxyServer.getEventManager().register(this, new PlayerJoinListener(null));
    }

    private void checkDataDirectory() {
        Path configFile = this.dataDirectory.resolve("client.json");
        if (!Files.exists(configFile)) {
            createFile(configFile);
        }
    }

    private void createFile(@NotNull Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (Exception e) {
            logger.severe("Failed to create file: " + path + " - " + e.getMessage());
        }
    }
}
