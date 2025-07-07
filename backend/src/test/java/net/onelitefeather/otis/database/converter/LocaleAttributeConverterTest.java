package net.onelitefeather.otis.database.converter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LocaleAttributeConverterTest {

    private static LocaleAttributeConverter localeAttributeConverter;

    @BeforeAll
    static void init() {
        localeAttributeConverter = new LocaleAttributeConverter();
    }

    @AfterAll
    static void teardown() {
        localeAttributeConverter = null;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "11111111111"})
    void testInvalidLocaleUsage(String locale) {
        assertEquals(Locale.ENGLISH, localeAttributeConverter.convertToEntityAttribute(locale));
    }

    @Test
    void testLocaleConversation() {
        Locale locale = Locale.GERMANY;
        String stringLocale = localeAttributeConverter.convertToDatabaseColumn(locale);
        assertNotNull(stringLocale);
        assertEquals(locale.toLanguageTag(), stringLocale);
    }
}
