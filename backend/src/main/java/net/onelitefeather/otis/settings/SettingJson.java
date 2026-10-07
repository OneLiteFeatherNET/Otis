package net.onelitefeather.otis.settings;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.otis.problem.InvalidSettingValueProblem;

/**
 * The strict Jackson mapper for setting values: standard JSON only, no duplicate keys, no trailing
 * content. Setting values are stored opaquely, so they must at least be well-formed.
 */
@Factory
public class SettingJson {

    /** @return a new strict mapper; a method of its own so unit tests can build the same one */
    public static ObjectMapper newMapper() {
        return JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    @Singleton
    ObjectMapper settingObjectMapper() {
        return newMapper();
    }

    /**
     * @param mapper the mapper from {@link #newMapper()}
     * @param body   the request body
     * @return the parsed JSON value
     * @throws InvalidSettingValueProblem if {@code body} is empty or not valid JSON
     */
    public static JsonNode parse(ObjectMapper mapper, String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidSettingValueProblem();
        }
        try {
            return mapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new InvalidSettingValueProblem();
        }
    }
}
