package net.onelitefeather.otis.client.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import net.onelitefeather.otis.client.data.OtisPlayer;
import net.onelitefeather.otis.client.data.OtisPlayerDTO;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OtisJacksonModule {

    public static final SimpleModule INSTANCE = createModule();

    private OtisJacksonModule() {
        throw new UnsupportedOperationException();
    }

    private static @NotNull SimpleModule createModule() {
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(OtisPlayer.class, OtisPlayerDTO.class);
        return module;
    }
}
