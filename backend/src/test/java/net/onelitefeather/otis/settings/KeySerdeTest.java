package net.onelitefeather.otis.settings;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.exceptions.SerdeException;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Round trip through the real Micronaut Serde mapper; each test gets its own context. */
class KeySerdeTest {

    private static final Argument<Key> KEY = Argument.of(Key.class);

    @Test
    void writesKeyAsPlainString() throws IOException {
        try (ApplicationContext context = ApplicationContext.run()) {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            assertEquals("\"lobby:player_hider\"", mapper.writeValueAsString(KEY, Key.key("lobby", "player_hider")),
                    "wire format of a key");
        }
    }

    @Test
    void readsKeyFromPlainString() throws IOException {
        try (ApplicationContext context = ApplicationContext.run()) {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            assertEquals(Key.key("olf", "language"), mapper.readValue("\"olf:language\"", KEY),
                    "key read from its wire format");
        }
    }

    @Test
    void rejectsKeyWithoutNamespaceOnRead() {
        try (ApplicationContext context = ApplicationContext.run()) {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            assertThrows(SerdeException.class, () -> mapper.readValue("\"player_hider\"", KEY),
                    "a key without namespace must not default to minecraft");
        }
    }
}
