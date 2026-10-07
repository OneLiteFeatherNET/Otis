package net.onelitefeather.otis.velocity.link;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Port to Otis' account link API. Calls block, so callers run them off the proxy's threads.
 */
public interface LinkGateway {

    @NotNull GatewayResult<IssuedCode> requestCode(@NotNull UUID player, @NotNull LinkProvider provider);

    @NotNull GatewayResult<List<LinkEntry>> listLinks(@NotNull UUID player);

    @NotNull GatewayResult<Void> unlink(@NotNull UUID player, @NotNull LinkProvider provider);

    @NotNull GatewayResult<LinkEntry> setProfileLink(@NotNull UUID player, @NotNull LinkProvider provider,
                                                     @NotNull String value);
}
