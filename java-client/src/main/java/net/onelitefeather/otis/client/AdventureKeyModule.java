package net.onelitefeather.otis.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;

import java.io.IOException;

/**
 * Jackson module that (de)serializes Adventure {@link Key}s as their plain {@code namespace:value}
 * string, the wire format of the Otis settings API. {@link OtisClients} registers it for you.
 * <p>
 * Requires {@code net.kyori:adventure-key} at runtime; every realistic consumer (Velocity, Paper,
 * Minestom) ships Adventure, so the client does not bundle it.
 */
public final class AdventureKeyModule extends SimpleModule {

    public AdventureKeyModule() {
        super("OtisAdventureKeyModule");
        addSerializer(Key.class, new KeySerializer());
        addDeserializer(Key.class, new KeyDeserializer());
    }

    private static final class KeySerializer extends JsonSerializer<Key> {
        @Override
        public void serialize(Key value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(value.asString());
        }
    }

    private static final class KeyDeserializer extends FromStringDeserializer<Key> {

        KeyDeserializer() {
            super(Key.class);
        }

        @Override
        protected Key _deserialize(String value, DeserializationContext context) throws IOException {
            try {
                return Key.key(value);
            } catch (InvalidKeyException exception) {
                return (Key) context.handleWeirdStringValue(Key.class, value, "not a valid Adventure key");
            }
        }
    }
}
