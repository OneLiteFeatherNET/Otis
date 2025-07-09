package net.theevilreaper.otis.database.converter;

import jakarta.persistence.AttributeConverter;

import java.util.Locale;

public final class LocaleAttributeConverter implements AttributeConverter<Locale, String> {

    private static final Locale DEFAULT = Locale.ENGLISH;

    @Override
    public String convertToDatabaseColumn(Locale attribute) {
        return attribute.toLanguageTag();
    }

    @Override
    public Locale convertToEntityAttribute(String dbData) {
        if (dbData != null && !dbData.trim().isEmpty()) {
            Locale locale = Locale.forLanguageTag(dbData);
            // If the input is invalid, locale.getLanguage() will be empty
            if (locale.getLanguage().isEmpty()) {
                return DEFAULT;
            }
            return locale;
        }
        return DEFAULT;
    }
}
