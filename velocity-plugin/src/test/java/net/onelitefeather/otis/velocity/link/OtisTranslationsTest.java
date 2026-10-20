package net.onelitefeather.otis.velocity.link;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtisTranslationsTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void germanLocaleRendersGermanText() {
        try (OtisTranslations translations = new OtisTranslations()) {
            translations.install();

            Component rendered = GlobalTranslator.render(Component.translatable(Messages.LIST_EMPTY), Locale.GERMAN);

            assertTrue(plain(rendered).startsWith("Du hast keine"), plain(rendered));
        }
    }

    @Test
    void regionalGermanLocaleRendersGermanText() {
        try (OtisTranslations translations = new OtisTranslations()) {
            translations.install();

            Component rendered = GlobalTranslator.render(Component.translatable(Messages.LIST_EMPTY),
                    Locale.of("de", "DE"));

            assertTrue(plain(rendered).startsWith("Du hast keine"), plain(rendered));
        }
    }

    @Test
    void otherLocalesFallBackToEnglish() {
        try (OtisTranslations translations = new OtisTranslations()) {
            translations.install();

            Component rendered = GlobalTranslator.render(Component.translatable(Messages.LIST_EMPTY), Locale.FRENCH);

            assertTrue(plain(rendered).startsWith("You have no linked accounts"), plain(rendered));
        }
    }

    @Test
    void closeUnregistersTheStoreFromTheGlobalTranslator() {
        OtisTranslations translations = new OtisTranslations();
        translations.install();
        translations.close();

        Component rendered = GlobalTranslator.render(Component.translatable(Messages.LIST_EMPTY), Locale.ENGLISH);

        assertEquals(Messages.LIST_EMPTY, plain(rendered), "key stays untranslated once the store is removed");
    }

    @Test
    void rendersWithoutGlobalRegistration() {
        try (OtisTranslations translations = new OtisTranslations()) {
            Component rendered = translations.render(Component.translatable(Messages.LIST_EMPTY), Locale.GERMAN);

            assertFalse(plain(rendered).startsWith("otis."), plain(rendered));
        }
    }
}
