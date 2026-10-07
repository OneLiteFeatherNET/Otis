package net.onelitefeather.otis.client;

import net.onelitefeather.otis.client.invoker.ApiClient;

/**
 * Entry point for clients that use the settings API, whose keys are Adventure
 * {@link net.kyori.adventure.key.Key}s: it returns an {@link ApiClient} whose JSON mapper knows them.
 *
 * <pre>{@code
 * ApiClient client = OtisClients.newApiClient().setHost("otis").setPort(8080);
 * PlayerSettingDTO setting = new PlayerSettingsApi(client).getPlayerSetting(uuid, Key.key("lobby", "player_hider"));
 * }</pre>
 * <p>
 * A client created with {@code new ApiClient()} keeps working for all other endpoints; only settings calls
 * need the key module.
 */
public final class OtisClients {

    private OtisClients() {
    }

    /**
     * @return a new {@link ApiClient} with the default configuration and Adventure key support
     */
    public static ApiClient newApiClient() {
        return configure(new ApiClient());
    }

    /**
     * Adds Adventure key support to an existing client (and keeps its other mapper settings).
     *
     * @param client the client to configure
     * @return {@code client}
     */
    public static ApiClient configure(ApiClient client) {
        return client.setObjectMapper(client.getObjectMapper().registerModule(new AdventureKeyModule()));
    }
}
