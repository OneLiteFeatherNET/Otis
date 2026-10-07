package net.onelitefeather.otis.settings;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.problem.OtisProblemException;

import java.io.IOException;

/**
 * Reads and writes an Adventure {@link Key} as its plain {@code namespace:value} string, so DTOs can
 * carry the key type itself.
 */
@Singleton
public final class KeySerde implements Serde<Key> {

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Key> type, Key value)
            throws IOException {
        encoder.encodeString(value.asString());
    }

    @Override
    public Key deserialize(Decoder decoder, DecoderContext context, Argument<? super Key> type)
            throws IOException {
        String raw = decoder.decodeString();
        try {
            return SettingKeys.parse(raw);
        } catch (OtisProblemException problem) {
            throw new SerdeException("Invalid setting key: " + problem.getDetail(), problem);
        }
    }
}
