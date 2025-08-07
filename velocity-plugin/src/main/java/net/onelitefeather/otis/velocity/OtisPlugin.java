package net.onelitefeather.otis.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.onelitefeather.otis.client.OtisClient;
import net.onelitefeather.otis.velocity.config.OtisConfig;
import net.onelitefeather.otis.velocity.listener.PlayerListener;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;

@Plugin(id = "otis", name = "Otis", version = "0.0.1",
        url = "https://onelitefeather.net", description = "We did it!", authors = {"OneLiteFeatherNET"})
public class OtisPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    
    private OtisConfig config;
    private OtisClient client;

    @Inject
    public OtisPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
    
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Load configuration
        this.config = OtisConfig.load(dataDirectory.resolve("config.json"));
        this.logger.info("Loaded configuration with base URL: {}", config.getBaseUrl());
        
        // Initialize client
        this.client = OtisClient.newClient(config.getBaseUrl(), Duration.ofSeconds(10));
        this.logger.info("Initialized Otis client");
        
        // Register event listeners
        this.server.getEventManager().register(this, new PlayerListener(this));
        this.logger.info("Registered event listeners");
        
        this.logger.info("Otis plugin initialized successfully");
    }
    
    /**
     * Gets the Otis client.
     *
     * @return the Otis client
     */
    public OtisClient getClient() {
        return client;
    }
    
    /**
     * Gets the plugin configuration.
     *
     * @return the configuration
     */
    public OtisConfig getConfig() {
        return config;
    }
    
    /**
     * Gets the logger.
     *
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }
}
