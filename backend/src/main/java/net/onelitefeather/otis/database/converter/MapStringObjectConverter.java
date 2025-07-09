package net.onelitefeather.otis.database.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import net.onelitefeather.otis.exception.ObjectConversationException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Converter
public final class MapStringObjectConverter implements AttributeConverter<Map<String, Object>, String> {

    public static final JavaType VALUE_TYPE = TypeFactory.defaultInstance()
            .constructParametricType(Map.class, String.class, Object.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException exception) {
            throw new ObjectConversationException("Could not convert Map to JSON.", exception);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, VALUE_TYPE);
        } catch (IOException exception) {
            throw new ObjectConversationException("Could not convert JSON to Map.", exception);
        }
    }
}
