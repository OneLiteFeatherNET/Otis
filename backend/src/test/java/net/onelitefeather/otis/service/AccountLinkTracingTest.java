package net.onelitefeather.otis.service;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import net.onelitefeather.otis.dto.RedeemRequestDTO;
import net.onelitefeather.otis.events.NoopOutboxWriter;
import net.onelitefeather.otis.links.LinkCodes;
import net.onelitefeather.otis.problem.OtisProblemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Spans of the link use cases, observed through an in-memory SDK owned by each test instance. */
class AccountLinkTracingTest {

    private static final String EXTERNAL_ID = "123456789012345678";
    private static final String DISPLAY_NAME = "secret-display-name";
    private static final String HANDLE = "secrethandle";

    // instance field on purpose: every test gets its own SDK and nothing is registered globally
    @RegisterExtension
    final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
    private final FakeLinkRepositories repositories = new FakeLinkRepositories();
    private final UUID player = UUID.randomUUID();
    private AccountLinkService service;

    @BeforeEach
    void setUp() {
        repositories.addPlayer(player);
        service = new AccountLinkService(repositories.links, repositories.codes, LinkTransactions.direct(), clock,
                new LinkCodes(new Random(1)), otel.getOpenTelemetry(), new NoopOutboxWriter());
    }

    private SpanData onlySpan() {
        List<SpanData> spans = otel.getSpans();
        assertEquals(1, spans.size(), "exactly one span, got " + spans.stream().map(SpanData::getName).toList());
        return spans.getFirst();
    }

    private SpanData lastSpan() {
        List<SpanData> spans = otel.getSpans();
        return spans.getLast();
    }

    private static String attribute(SpanData span, String name) {
        return span.getAttributes().asMap().entrySet().stream()
                .filter(e -> e.getKey().getKey().equals(name)).map(e -> String.valueOf(e.getValue()))
                .findFirst().orElse(null);
    }

    private static void assertInternalSpan(SpanData span, String name, String provider, String outcome) {
        assertEquals(name, span.getName(), "span name");
        assertEquals(SpanKind.INTERNAL, span.getKind(), "span kind");
        assertEquals(provider, attribute(span, "otis.link.provider"), "provider");
        assertEquals(outcome, attribute(span, "otis.link.outcome"), "outcome");
        assertNotEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), "expected outcomes are not errors");
    }

    @Test
    void issuingACodeCreatesOneSpanWithPlayerProviderAndOutcome() {
        service.issueCode(player, "discord");

        SpanData span = onlySpan();
        assertInternalSpan(span, "links.code.create", "discord", "issued");
        assertEquals(player.toString(), attribute(span, "otis.player.uuid"), "player uuid");
    }

    @Test
    void rateLimitingIsAnExpectedOutcome() {
        for (int i = 0; i < 5; i++) {
            service.issueCode(player, "discord");
        }
        assertThrows(OtisProblemException.class, () -> service.issueCode(player, "discord"));

        assertInternalSpan(lastSpan(), "links.code.create", "discord", "rate_limited");
    }

    @Test
    void redeemRecordsLinkedWithPlayerAndNoSecrets() {
        String code = service.issueCode(player, "discord").code();

        service.redeem(new RedeemRequestDTO(code, "discord", EXTERNAL_ID, DISPLAY_NAME));

        SpanData span = lastSpan();
        assertInternalSpan(span, "links.redeem", "discord", "linked");
        assertEquals(player.toString(), attribute(span, "otis.player.uuid"), "player uuid");
        span.getAttributes().forEach((key, value) -> {
            String text = String.valueOf(value);
            assertFalse(text.contains(code) || text.contains(code.replace("-", "")), key + " must not carry the code");
            assertFalse(text.contains(EXTERNAL_ID), key + " must not carry the externalId");
            assertFalse(text.contains(DISPLAY_NAME), key + " must not carry the displayName");
        });
    }

    @Test
    void redeemOverAnUnverifiedLinkRecordsUpgraded() {
        service.putUnverified(player, "twitch", HANDLE + "xx");
        String code = service.issueCode(player, "twitch").code();

        service.redeem(new RedeemRequestDTO(code, "twitch", EXTERNAL_ID, null));

        assertInternalSpan(lastSpan(), "links.redeem", "twitch", "upgraded");
    }

    @Test
    void anExpiredCodeIsInvalidAndNotAnError() {
        String code = service.issueCode(player, "discord").code();
        clock.advance(Duration.ofMinutes(11));

        assertThrows(OtisProblemException.class,
                () -> service.redeem(new RedeemRequestDTO(code, "discord", EXTERNAL_ID, DISPLAY_NAME)));

        SpanData span = lastSpan();
        assertInternalSpan(span, "links.redeem", "discord", "invalid");
        assertNull(attribute(span, "otis.player.uuid"), "the player of an invalid code is not revealed");
    }

    @Test
    void aConflictIsAnExpectedOutcome() {
        service.putUnverified(player, "github", "onelitefeather");
        repositories.links.findByPlayerIdAndProvider(repositories.links.findPlayerIds(player).getFirst(), "github")
                .ifPresent(link -> link.setVerified(true));
        String code = service.issueCode(player, "github").code();

        assertThrows(OtisProblemException.class,
                () -> service.redeem(new RedeemRequestDTO(code, "github", EXTERNAL_ID, DISPLAY_NAME)));

        assertInternalSpan(lastSpan(), "links.redeem", "github", "conflict");
    }

    @Test
    void putRecordsSetAndNeverTheValue() {
        service.putUnverified(player, "github", HANDLE);

        SpanData span = onlySpan();
        assertInternalSpan(span, "links.put", "github", "set");
        span.getAttributes().forEach((key, value) ->
                assertFalse(String.valueOf(value).contains(HANDLE), key + " must not carry the link value"));
    }

    @Test
    void anInvalidValueIsRejectedNotAnError() {
        assertThrows(OtisProblemException.class, () -> service.putUnverified(player, "twitch", "https://evil.example/x"));

        assertInternalSpan(onlySpan(), "links.put", "twitch", "rejected");
    }

    @Test
    void listDeleteAndLookupHaveTheirOwnSpans() {
        service.list(player);
        assertInternalSpan(lastSpan(), "links.list", null, "found");

        service.delete(player, "discord");
        assertInternalSpan(lastSpan(), "links.delete", "discord", "not_found");

        assertThrows(OtisProblemException.class, () -> service.lookup("discord", EXTERNAL_ID));
        SpanData lookup = lastSpan();
        assertInternalSpan(lookup, "links.lookup", "discord", "not_found");
        assertNull(attribute(lookup, "otis.player.uuid"), "no player is known for a missed lookup");
        lookup.getAttributes().forEach((key, value) ->
                assertFalse(String.valueOf(value).contains(EXTERNAL_ID), key + " must not carry the externalId"));
    }

    @Test
    void deletingAnExistingLinkRecordsDeleted() {
        service.putUnverified(player, "github", HANDLE);

        service.delete(player, "github");

        assertInternalSpan(lastSpan(), "links.delete", "github", "deleted");
    }

    @Test
    void aSuccessfulLookupRecordsFound() {
        String code = service.issueCode(player, "discord").code();
        service.redeem(new RedeemRequestDTO(code, "discord", EXTERNAL_ID, DISPLAY_NAME));

        service.lookup("discord", EXTERNAL_ID);

        SpanData span = lastSpan();
        assertInternalSpan(span, "links.lookup", "discord", "found");
        assertEquals(player.toString(), attribute(span, "otis.player.uuid"), "player uuid of the found link");
    }

    @Test
    void anUnexpectedFailureMarksTheSpanAsError() {
        repositories.links.failNextSave(new IllegalStateException("database down"), () -> { });

        assertThrows(IllegalStateException.class, () -> service.putUnverified(player, "github", HANDLE));

        assertEquals(StatusCode.ERROR, onlySpan().getStatus().getStatusCode(), "unexpected failures are errors");
    }

    @Test
    void anUnsupportedProviderIsRejectedNotAnError() {
        assertThrows(OtisProblemException.class, () -> service.issueCode(player, "myspace"));

        SpanData span = onlySpan();
        assertInternalSpan(span, "links.code.create", null, "rejected");
    }
}
