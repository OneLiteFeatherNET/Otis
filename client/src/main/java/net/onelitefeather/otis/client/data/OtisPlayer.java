package net.onelitefeather.otis.client.data;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public sealed interface OtisPlayer permits OtisPlayerDTO {

    @NotNull UUID uuid();

    @NotNull String playerName();

    long firstJoin();

    long lastJoin();

    @NotNull Map<String, Object> profileTextures();
}
