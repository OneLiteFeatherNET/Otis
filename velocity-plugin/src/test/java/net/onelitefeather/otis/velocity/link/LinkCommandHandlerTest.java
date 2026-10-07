package net.onelitefeather.otis.velocity.link;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final FakeGateway gateway = new FakeGateway();
    private final CapturingAudience audience = new CapturingAudience();
    private final Queue<Runnable> queued = new ArrayDeque<>();
    private ListAppender<ILoggingEvent> logs;
    private LinkCommandHandler handler;

    @BeforeEach
    void setUp() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("test-" + UUID.randomUUID());
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        handler = new LinkCommandHandler(gateway, Runnable::run, logger, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- /link ---

    @Test
    void linkShowsTheCodeAsCopyableComponentWithExpiry() {
        gateway.codeResult = new GatewayResult.Success<>(new IssuedCode("K7Q4-MZ2A", NOW.plusSeconds(600)));

        handler.requestCode(audience, PLAYER, "discord");

        assertEquals(List.of(Messages.CODE_ISSUED), audience.keys());
        assertEquals(List.of("requestCode discord"), gateway.calls);
        assertEquals("K7Q4-MZ2A", audience.copiedText(0, Locale.ENGLISH).orElse(null),
                "the code component must copy the code to the clipboard");
        String text = audience.plain(Locale.ENGLISH).getFirst();
        assertTrue(text.contains("K7Q4-MZ2A"), text);
        assertTrue(text.contains("10 min"), "expiry must be shown: " + text);
        assertTrue(text.contains("discord"), "provider must be shown: " + text);
    }

    @Test
    void linkAcceptsTheProviderCaseInsensitively() {
        gateway.codeResult = new GatewayResult.Success<>(new IssuedCode("K7Q4-MZ2A", NOW.plusSeconds(600)));

        handler.requestCode(audience, PLAYER, "Discord");

        assertEquals(List.of("requestCode discord"), gateway.calls);
    }

    @Test
    void linkRoundsPartialMinutesUpAndNeverShowsZero() {
        gateway.codeResult = new GatewayResult.Success<>(new IssuedCode("K7Q4-MZ2A", NOW.plusSeconds(5)));

        handler.requestCode(audience, PLAYER, "twitch");

        assertTrue(audience.plain(Locale.ENGLISH).getFirst().contains("1 min"), audience.plain(Locale.ENGLISH).toString());
    }

    @Test
    void linkRejectsProvidersWithoutCodeLinkingWithoutCallingOtis() {
        handler.requestCode(audience, PLAYER, "github");

        assertEquals(List.of(Messages.UNSUPPORTED_FOR_LINK), audience.keys());
        assertTrue(gateway.calls.isEmpty(), "no request may be sent: " + gateway.calls);
        assertTrue(audience.plain(Locale.ENGLISH).getFirst().contains("/social"));
    }

    @Test
    void linkRejectsUnknownProvidersWithoutCallingOtis() {
        handler.requestCode(audience, PLAYER, "myspace");

        assertEquals(List.of(Messages.ERROR_UNSUPPORTED_PROVIDER), audience.keys());
        assertTrue(gateway.calls.isEmpty());
    }

    @Test
    void linkReportsRateLimiting() {
        gateway.codeResult = new GatewayResult.Problem<>("link-code-rate-limited");

        handler.requestCode(audience, PLAYER, "discord");

        assertEquals(List.of(Messages.ERROR_RATE_LIMITED), audience.keys());
    }

    @Test
    void linkReportsAlreadyVerifiedProvider() {
        gateway.codeResult = new GatewayResult.Problem<>("provider-already-linked");

        handler.requestCode(audience, PLAYER, "discord");

        assertEquals(List.of(Messages.ERROR_ALREADY_VERIFIED), audience.keys());
    }

    @Test
    void linkReportsUnknownPlayer() {
        gateway.codeResult = new GatewayResult.Problem<>("player-not-found");

        handler.requestCode(audience, PLAYER, "discord");

        assertEquals(List.of(Messages.ERROR_PLAYER_UNKNOWN), audience.keys());
    }

    @Test
    void unknownProblemsFallBackToTheGenericMessageAndLogOnce() {
        gateway.codeResult = new GatewayResult.Problem<>("something-new");

        handler.requestCode(audience, PLAYER, "discord");

        assertEquals(List.of(Messages.ERROR_UNAVAILABLE), audience.keys());
        assertEquals(1, warnings().size(), "exactly one WARN expected: " + warnings());
    }

    // --- /links ---

    @Test
    void linksMarksVerifiedAndUnverifiedEntries() {
        gateway.listResult = new GatewayResult.Success<>(List.of(
                new LinkEntry("discord", "Steve#0001", true),
                new LinkEntry("youtube", "https://www.youtube.com/@olf", false)));

        handler.list(audience, PLAYER);

        assertEquals(List.of(Messages.LIST_HEADER, Messages.LIST_ENTRY_VERIFIED, Messages.LIST_ENTRY_UNVERIFIED),
                audience.keys());
        List<String> text = audience.plain(Locale.ENGLISH);
        assertTrue(text.get(1).contains("verified") && text.get(1).contains("discord") && text.get(1).contains("Steve#0001"), text.get(1));
        assertTrue(text.get(2).contains("unverified") && text.get(2).contains("youtube"), text.get(2));
    }

    @Test
    void linksExplainsHowToLinkWhenThereAreNone() {
        gateway.listResult = new GatewayResult.Success<>(List.of());

        handler.list(audience, PLAYER);

        assertEquals(List.of(Messages.LIST_EMPTY), audience.keys());
        assertTrue(audience.plain(Locale.ENGLISH).getFirst().contains("/link"));
    }

    @Test
    void linksRendersGermanForGermanPlayers() {
        gateway.listResult = new GatewayResult.Success<>(List.of());

        handler.list(audience, PLAYER);

        assertTrue(audience.plain(Locale.GERMAN).getFirst().startsWith("Du hast keine"));
    }

    // --- /unlink ---

    @Test
    void unlinkConfirmsRemoval() {
        gateway.unlinkResult = new GatewayResult.Success<>(null);

        handler.unlink(audience, PLAYER, "discord");

        assertEquals(List.of("unlink discord"), gateway.calls);
        assertEquals(List.of(Messages.UNLINKED), audience.keys());
        assertTrue(audience.plain(Locale.ENGLISH).getFirst().contains("discord"));
    }

    @Test
    void unlinkRejectsUnknownProvidersWithoutCallingOtis() {
        handler.unlink(audience, PLAYER, "myspace");

        assertEquals(List.of(Messages.ERROR_UNSUPPORTED_PROVIDER), audience.keys());
        assertTrue(gateway.calls.isEmpty());
    }

    // --- /social ---

    @Test
    void socialConfirmsTheProfileLink() {
        gateway.profileResult = new GatewayResult.Success<>(
                new LinkEntry("youtube", "https://www.youtube.com/@onelitefeather", false));

        handler.setProfileLink(audience, PLAYER, "youtube", "https://www.youtube.com/@onelitefeather");

        assertEquals(List.of("setProfileLink youtube https://www.youtube.com/@onelitefeather"), gateway.calls);
        assertEquals(List.of(Messages.SOCIAL_SET), audience.keys());
        assertTrue(audience.plain(Locale.ENGLISH).getFirst().contains("https://www.youtube.com/@onelitefeather"));
    }

    @Test
    void socialExplainsInvalidValues() {
        gateway.profileResult = new GatewayResult.Problem<>("invalid-link-value");

        handler.setProfileLink(audience, PLAYER, "github", "not a handle!");

        assertEquals(List.of(Messages.ERROR_INVALID_VALUE), audience.keys());
        assertTrue(audience.plain(Locale.ENGLISH).getFirst().contains("github"));
    }

    @Test
    void socialProtectsVerifiedLinks() {
        gateway.profileResult = new GatewayResult.Problem<>("provider-already-linked");

        handler.setProfileLink(audience, PLAYER, "discord", "steve");

        assertEquals(List.of(Messages.ERROR_ALREADY_VERIFIED), audience.keys());
        assertTrue(audience.plain(Locale.ENGLISH).getFirst().contains("/unlink discord"));
    }

    // --- availability and threading ---

    @Test
    void unavailableOtisShowsTryAgainLaterAndLogsOneWarningWithoutPersonalData() {
        gateway.codeResult = new GatewayResult.Unavailable<>("ConnectException");

        handler.requestCode(audience, PLAYER, "discord");

        assertEquals(List.of(Messages.ERROR_UNAVAILABLE), audience.keys());
        List<ILoggingEvent> warnings = warnings();
        assertEquals(1, warnings.size(), "exactly one WARN expected: " + warnings);
        String line = warnings.getFirst().getFormattedMessage();
        assertTrue(line.contains("discord") && line.contains("ConnectException"), line);
        assertFalse(line.contains(PLAYER.toString()), "uuid must not be logged: " + line);
    }

    @Test
    void aGatewayThatThrowsIsTreatedAsUnavailable() {
        LinkGateway exploding = new FakeGateway() {
            @Override
            public GatewayResult<List<LinkEntry>> listLinks(UUID player) {
                throw new IllegalStateException("boom");
            }
        };
        LinkCommandHandler failing = new LinkCommandHandler(exploding, Runnable::run, logger(), Clock.fixed(NOW, ZoneOffset.UTC));

        failing.list(audience, PLAYER);

        assertEquals(List.of(Messages.ERROR_UNAVAILABLE), audience.keys());
    }

    @Test
    void commandsReturnBeforeOtisIsCalled() {
        Executor deferred = queued::add;
        gateway.listResult = new GatewayResult.Success<>(List.of());
        LinkCommandHandler async = new LinkCommandHandler(gateway, deferred, logger(), Clock.fixed(NOW, ZoneOffset.UTC));

        async.list(audience, PLAYER);

        assertTrue(gateway.calls.isEmpty(), "the gateway must not be called on the caller's thread");
        assertTrue(audience.messages().isEmpty());
        assertEquals(1, queued.size());

        queued.remove().run();

        assertEquals(List.of("listLinks"), gateway.calls);
        assertEquals(List.of(Messages.LIST_EMPTY), audience.keys());
    }

    private List<ILoggingEvent> warnings() {
        return logs.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    }

    private org.slf4j.Logger logger() {
        return (org.slf4j.Logger) LoggerFactory.getLogger("test-" + UUID.randomUUID());
    }
}
