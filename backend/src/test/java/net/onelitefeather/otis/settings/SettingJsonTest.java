package net.onelitefeather.otis.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.onelitefeather.otis.problem.InvalidSettingValueProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingJsonTest {

    private final ObjectMapper mapper = SettingJson.newMapper();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "{not json", "{} {}", "{\"a\":1,\"a\":2}", "[1,", "{'a':1}", "// c\n1", "NaN"})
    void invalidBodiesAreRejected(String body) {
        assertThrows(InvalidSettingValueProblem.class, () -> SettingJson.parse(mapper, body),
                "'" + body + "' is not a valid single JSON value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "1", "2.5", "\"text\"", "[]", "{}", "{\"a\":[1,{\"b\":null}]}"})
    void everyJsonValueKindIsAccepted(String body) {
        assertEquals(body, SettingJson.parse(mapper, body).toString(), "round trip of " + body);
    }

    @Test
    void whitespaceAroundTheValueIsAccepted() {
        assertTrue(SettingJson.parse(mapper, " \n{\"a\":1}\n ").isObject(), "surrounding whitespace is fine");
    }
}
