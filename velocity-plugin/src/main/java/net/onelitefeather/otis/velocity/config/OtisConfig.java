package net.onelitefeather.otis.velocity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration class for the Otis Velocity plugin.
 */
public class OtisConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private String baseUrl;

    /**
     * Creates a new configuration with default values.
     */
    public OtisConfig() {
        this.baseUrl = "http://localhost:8080";
    }

    /**
     * Gets the base URL for the Otis API.
     *
     * @return the base URL
     */
    public @NotNull String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the base URL for the Otis API.
     *
     * @param baseUrl the base URL
     */
    public void setBaseUrl(@NotNull String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Loads the configuration from the specified file.
     *
     * @param path the path to the configuration file
     * @return the loaded configuration
     */
    public static @NotNull OtisConfig load(@NotNull Path path) {
        try {
            if (Files.notExists(path)) {
                OtisConfig config = new OtisConfig();
                save(config, path);
                return config;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                return GSON.fromJson(reader, OtisConfig.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Saves the configuration to the specified file.
     *
     * @param config the configuration to save
     * @param path   the path to the configuration file
     */
    public static void save(@NotNull OtisConfig config, @NotNull Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration", e);
        }
    }
}