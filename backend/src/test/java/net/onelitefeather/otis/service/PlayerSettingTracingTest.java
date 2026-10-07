package net.onelitefeather.otis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import net.onelitefeather.otis.problem.OtisProblemException;
import net.onelitefeather.otis.settings.SettingJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Spans of the settings use cases, observed through an in-memory SDK owned by each test instance. */
class PlayerSettingTracingTest {

    // instance field on purpose: every test gets its own SDK and nothing is registered globally
    @RegisterExtension
    final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

    private static final String SECRET = "super-secret-value-42";

    private final ObjectMapper mapper = SettingJson.newMapper();
    private final FakePlayerSettingRepository repository = new FakePlayerSettingRepository();
    private final UUID player = UUID.randomUUID();
    private PlayerSettingService service;

    @BeforeEach
    void setUp() {
        repository.addPlayer(player);
        service = new PlayerSettingService(repository, mapper, new MutableClock(Instant.parse("2026-01-01T00:00:00Z")),
                otel.getOpenTelemetry());
    }

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    private SpanData onlySpan() {
        List<SpanData> spans = otel.getSpans();
        assertEquals(1, spans.size(), "exactly one span per use case, got "
                + spans.stream().map(SpanData::getName).toList());
        return spans.getFirst();
    }

    private static String attribute(SpanData span, String name) {
        return span.getAttributes().asMap().entrySet().stream()
                .filter(e -> e.getKey().getKey().equals(name)).map(e -> String.valueOf(e.getValue()))
                .findFirst().orElse(null);
    }

    @Test
    void putCreatesOneInternalSpanWithPlayerKeyAndOutcome() throws Exception {
        service.put(player, "lobby:player_hider", json("{\"secret\":\"" + SECRET + "\"}"));

        SpanData span = onlySpan();
        assertEquals("settings.put", span.getName(), "span name");
        assertEquals(SpanKind.INTERNAL, span.getKind(), "span kind");
        assertEquals(player.toString(), attribute(span, "otis.player.uuid"), "player uuid");
        assertEquals("lobby", attribute(span, "otis.setting.namespace"), "namespace");
        assertEquals("lobby:player_hider", attribute(span, "otis.setting.key"), "key");
        assertEquals("created", attribute(span, "otis.setting.outcome"), "outcome");
    }

    @Test
    void putOutcomeDistinguishesUpdatedAndUnchanged() throws Exception {
        service.put(player, "lobby:player_hider", json("1"));
        service.put(player, "lobby:player_hider", json("2"));
        service.put(player, "lobby:player_hider", json("2"));

        List<String> outcomes = otel.getSpans().stream().map(s -> attribute(s, "otis.setting.outcome")).toList();
        assertEquals(List.of("created", "updated", "unchanged"), outcomes, "outcomes in call order");
    }

    @Test
    void getRecordsFoundAndNotFoundWithoutErrorStatus() throws Exception {
        service.put(player, "lobby:player_hider", json("true"));
        otel.clearSpans();

        service.get(player, "lobby:player_hider");
        assertThrows(OtisProblemException.class, () -> service.get(player, "lobby:missing"), "missing setting");

        List<SpanData> spans = otel.getSpans();
        assertEquals(List.of("found", "not_found"), spans.stream().map(s -> attribute(s, "otis.setting.outcome")).toList(),
                "outcomes");
        spans.forEach(s -> assertNotEquals(StatusCode.ERROR, s.getStatus().getStatusCode(),
                "expected outcomes must not be errors: " + s.getName()));
    }

    @Test
    void deleteRecordsDeletedAndNotFound() throws Exception {
        service.put(player, "lobby:player_hider", json("true"));
        otel.clearSpans();

        service.delete(player, "lobby:player_hider");
        service.delete(player, "lobby:player_hider");

        assertEquals(List.of("deleted", "not_found"),
                otel.getSpans().stream().map(s -> attribute(s, "otis.setting.outcome")).toList(), "outcomes");
        assertEquals("settings.delete", otel.getSpans().getFirst().getName(), "span name");
    }

    @Test
    void listRecordsNamespaceFilterAndFoundOutcome() throws Exception {
        service.put(player, "lobby:player_hider", json("true"));
        otel.clearSpans();

        service.list(player, List.of("olf", "lobby"));

        SpanData span = onlySpan();
        assertEquals("settings.list", span.getName(), "span name");
        assertEquals("[olf, lobby]", attribute(span, "otis.setting.namespaces"), "requested namespaces");
        assertEquals("found", attribute(span, "otis.setting.outcome"), "outcome");
    }

    @Test
    void rejectedKeyIsRecordedAsRejectedWithoutErrorStatus() {
        assertThrows(OtisProblemException.class, () -> service.put(player, "player_hider", mapper.valueToTree(1)),
                "key without namespace");

        SpanData span = onlySpan();
        assertEquals("rejected", attribute(span, "otis.setting.outcome"), "outcome");
        assertNotEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), "rejected input is not an error");
    }

    @Test
    void unknownPlayerIsRecordedAsNotFoundWithoutErrorStatus() throws Exception {
        UUID unknown = UUID.randomUUID();
        assertThrows(OtisProblemException.class, () -> service.put(unknown, "olf:language", json("\"de_de\"")),
                "unknown player");

        SpanData span = onlySpan();
        assertEquals("not_found", attribute(span, "otis.setting.outcome"), "outcome");
        assertEquals(unknown.toString(), attribute(span, "otis.player.uuid"), "player uuid");
        assertNotEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), "not found is not an error");
    }

    @Test
    void unexpectedFailureMarksTheSpanAsError() {
        repository.failNextSave(new RuntimeException("db down", new SQLException("down", "08006")), () -> { });

        assertThrows(RuntimeException.class, () -> service.put(player, "lobby:player_hider", mapper.valueToTree(1)),
                "unexpected failure");

        assertEquals(StatusCode.ERROR, onlySpan().getStatus().getStatusCode(), "unexpected failures are errors");
    }

    @Test
    void spanIsChildOfTheCurrentSpan() throws Exception {
        Span parent = otel.getOpenTelemetry().getTracer("test").spanBuilder("server").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            service.put(player, "lobby:player_hider", json("true"));
        } finally {
            parent.end();
        }

        SpanData child = otel.getSpans().stream().filter(s -> s.getName().equals("settings.put")).findFirst().orElseThrow();
        assertEquals(parent.getSpanContext().getSpanId(), child.getParentSpanId(), "parent span");
        assertEquals(parent.getSpanContext().getTraceId(), child.getTraceId(), "same trace");
    }

    @Test
    void settingValueNeverAppearsInSpans() throws Exception {
        service.put(player, "lobby:player_hider", json("{\"secret\":\"" + SECRET + "\"}"));
        service.get(player, "lobby:player_hider");
        service.list(player, List.of());
        service.delete(player, "lobby:player_hider");

        otel.getSpans().forEach(span -> {
            assertFalse(span.getAttributes().toString().contains(SECRET), "attributes of " + span.getName());
            assertFalse(span.getEvents().toString().contains(SECRET), "events of " + span.getName());
            assertFalse(span.getName().contains(SECRET), "name of " + span.getName());
        });
    }
}
