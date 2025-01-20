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
            return Locale.forLanguageTag(dbData);
        }
        return DEFAULT;
    }
}
