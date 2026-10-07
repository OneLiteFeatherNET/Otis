package net.onelitefeather.otis.velocity.link;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scriptable {@link LinkGateway} that records every call.
 */
class FakeGateway implements LinkGateway {

    final List<String> calls = new ArrayList<>();

    GatewayResult<IssuedCode> codeResult = new GatewayResult.Unavailable<>("unset");
    GatewayResult<List<LinkEntry>> listResult = new GatewayResult.Unavailable<>("unset");
    GatewayResult<Void> unlinkResult = new GatewayResult.Unavailable<>("unset");
    GatewayResult<LinkEntry> profileResult = new GatewayResult.Unavailable<>("unset");

    @Override
    public @NotNull GatewayResult<IssuedCode> requestCode(@NotNull UUID player, @NotNull LinkProvider provider) {
        calls.add("requestCode " + provider.id());
        return codeResult;
    }

    @Override
    public @NotNull GatewayResult<List<LinkEntry>> listLinks(@NotNull UUID player) {
        calls.add("listLinks");
        return listResult;
    }

    @Override
    public @NotNull GatewayResult<Void> unlink(@NotNull UUID player, @NotNull LinkProvider provider) {
        calls.add("unlink " + provider.id());
        return unlinkResult;
    }

    @Override
    public @NotNull GatewayResult<LinkEntry> setProfileLink(@NotNull UUID player, @NotNull LinkProvider provider,
                                                            @NotNull String value) {
        calls.add("setProfileLink " + provider.id() + " " + value);
        return profileResult;
    }
}
