package net.onelitefeather.otis.velocity.link;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleConsistencyTest {

    @Test
    void everyEnglishKeyExistsInGerman() throws IOException {
        Set<String> missing = new TreeSet<>(keys(OtisTranslations.bundle(Locale.ENGLISH)));
        missing.removeAll(keys(OtisTranslations.bundle(Locale.GERMAN)));

        assertTrue(missing.isEmpty(), "keys missing in otis_de: " + missing);
    }

    @Test
    void everyGermanKeyExistsInEnglish() throws IOException {
        Set<String> missing = new TreeSet<>(keys(OtisTranslations.bundle(Locale.GERMAN)));
        missing.removeAll(keys(OtisTranslations.bundle(Locale.ENGLISH)));

        assertTrue(missing.isEmpty(), "keys missing in otis_en: " + missing);
    }

    @Test
    void everyMessagesConstantExistsInFallbackBundle() throws IOException {
        Set<String> declared = messageKeys();
        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(keys(OtisTranslations.bundle(Locale.ENGLISH)));

        assertFalse(declared.isEmpty(), "Messages declares no keys");
        assertTrue(missing.isEmpty(), "Messages keys missing in the fallback bundle: " + missing);
    }

    @Test
    void everyFallbackKeyIsDeclaredInMessages() throws IOException {
        Set<String> unused = new TreeSet<>(keys(OtisTranslations.bundle(Locale.ENGLISH)));
        unused.removeAll(messageKeys());

        assertTrue(unused.isEmpty(), "bundle keys without a Messages constant: " + unused);
    }

    @Test
    void allKeysLiveUnderTheLinkNamespace() {
        for (String key : messageKeys()) {
            assertTrue(key.startsWith("otis.link."), "key outside otis.link.: " + key);
        }
    }

    @Test
    void bundlesAreReadAsUtf8() throws IOException {
        String text = OtisTranslations.bundle(Locale.GERMAN).getString(Messages.LIST_EMPTY);

        assertTrue(text.contains("verknüpften"), "German umlauts must survive loading: " + text);
    }

    @Test
    void bundleFilesAreValidUtf8() throws IOException {
        for (String file : new String[]{"/lang/otis_en.properties", "/lang/otis_de.properties"}) {
            try (InputStream in = BundleConsistencyTest.class.getResourceAsStream(file)) {
                byte[] bytes = in.readAllBytes();
                String decoded = new String(bytes, StandardCharsets.UTF_8);
                assertEquals(bytes.length, decoded.getBytes(StandardCharsets.UTF_8).length,
                        file + " is not valid UTF-8");
            }
        }
    }

    private static Set<String> keys(ResourceBundle bundle) {
        return new TreeSet<>(bundle.keySet());
    }

    private static Set<String> messageKeys() {
        Set<String> keys = new TreeSet<>();
        for (Field field : Messages.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    keys.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return keys;
    }
}
