package net.onelitefeather.otis.service;

import io.opentelemetry.api.OpenTelemetry;
import net.onelitefeather.otis.database.entity.AccountLink;
import net.onelitefeather.otis.dto.AccountLinkDTO;
import net.onelitefeather.otis.dto.LinkCodeDTO;
import net.onelitefeather.otis.dto.LinkLookupDTO;
import net.onelitefeather.otis.dto.RedeemRequestDTO;
import net.onelitefeather.otis.events.NoopOutboxWriter;
import net.onelitefeather.otis.links.LinkCodes;
import net.onelitefeather.otis.problem.ExternalAccountAlreadyLinkedProblem;
import net.onelitefeather.otis.problem.InvalidLinkValueProblem;
import net.onelitefeather.otis.problem.LinkCodeInvalidProblem;
import net.onelitefeather.otis.problem.LinkCodeRateLimitedProblem;
import net.onelitefeather.otis.problem.LinkNotFoundProblem;
import net.onelitefeather.otis.problem.PlayerNotFoundProblem;
import net.onelitefeather.otis.problem.ProviderAlreadyLinkedProblem;
import net.onelitefeather.otis.problem.UnsupportedProviderProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The link rules against in-memory repositories, a fixed clock and a seeded code generator. */
class AccountLinkServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final String DISCORD_ACCOUNT = "123456789012345678";

    private final MutableClock clock = new MutableClock(START);
    private final FakeLinkRepositories repositories = new FakeLinkRepositories();
    private final UUID player = UUID.randomUUID();
    private UUID playerId;
    private AccountLinkService service;

    @BeforeEach
    void setUp() {
        playerId = repositories.addPlayer(player);
        service = newService();
    }

    private AccountLinkService newService() {
        return new AccountLinkService(repositories.links, repositories.codes, LinkTransactions.direct(), clock,
                new LinkCodes(new Random(1)), OpenTelemetry.noop(), new NoopOutboxWriter());
    }

    private String issue(String provider) {
        return service.issueCode(player, provider).code();
    }

    private LinkLookupDTO redeem(String code, String provider, String externalId) {
        return service.redeem(new RedeemRequestDTO(code, provider, externalId, "display"));
    }

    // --- issuing -------------------------------------------------------------------------------------------

    @Test
    void issuedCodeExpiresTenMinutesAfterIssue() {
        LinkCodeDTO issued = service.issueCode(player, "discord");

        assertEquals("discord", issued.provider(), "provider");
        assertEquals(START.plus(Duration.ofMinutes(10)), issued.expiresAt(), "expiry");
        assertTrue(issued.code().matches("^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$"), "format of " + issued.code());
    }

    @Test
    void codeIsStoredOnlyAsHash() {
        String code = issue("discord");

        String stored = repositories.codes.all().getFirst().getCodeHash();
        assertEquals(LinkCodes.hash(LinkCodes.normalize(code).orElseThrow()), stored, "the hash of the normalized code");
        assertFalse(stored.contains(code.replace("-", "")), "the plain code must not be stored");
    }

    @Test
    void issuingForAnUnknownPlayerIsAProblem() {
        assertThrows(PlayerNotFoundProblem.class, () -> service.issueCode(UUID.randomUUID(), "discord"));
    }

    @Test
    void issuingForAnUnknownProviderIsAProblem() {
        assertThrows(UnsupportedProviderProblem.class, () -> service.issueCode(player, "myspace"));
    }

    @Test
    void newCodeRevokesThePreviousOpenCodeOfTheSameProvider() {
        String first = issue("discord");
        String second = issue("discord");

        assertThrows(LinkCodeInvalidProblem.class, () -> redeem(first, "discord", DISCORD_ACCOUNT), "old code");
        assertEquals(playerUuidOf(redeem(second, "discord", DISCORD_ACCOUNT)), player, "new code");
    }

    @Test
    void codesOfDifferentProvidersAreIndependent() {
        String discord = issue("discord");
        String twitch = issue("twitch");

        redeem(discord, "discord", DISCORD_ACCOUNT);
        redeem(twitch, "twitch", "twitch-42");

        assertEquals(2, service.list(player).size(), "both providers linked");
    }

    @Test
    void sixthCodeWithinAnHourIsRateLimited() {
        for (int i = 0; i < 5; i++) {
            issue("discord");
        }

        assertThrows(LinkCodeRateLimitedProblem.class, () -> issue("discord"));
    }

    @Test
    void codeCanBeIssuedAgainWhenTheWindowHasPassed() {
        for (int i = 0; i < 5; i++) {
            issue("discord");
            clock.advance(Duration.ofSeconds(1));
        }
        clock.advance(Duration.ofMinutes(60));

        assertEquals("discord", service.issueCode(player, "discord").provider(), "issued after the window");
    }

    @Test
    void issuingDeletesThePlayersCodesOlderThanADay() {
        issue("discord");
        clock.advance(Duration.ofHours(25));
        issue("twitch");

        assertEquals(1, repositories.codes.all().size(), "only the fresh code remains");
    }

    // --- redeeming -----------------------------------------------------------------------------------------

    @Test
    void redeemingAValidCodeCreatesAVerifiedLink() {
        String code = issue("discord");

        LinkLookupDTO linked = service.redeem(new RedeemRequestDTO(code, "discord", DISCORD_ACCOUNT, "meinerlp"));

        assertEquals(player, linked.playerUuid(), "player");
        AccountLinkDTO link = linked.link();
        assertEquals("discord", link.provider(), "provider");
        assertEquals(DISCORD_ACCOUNT, link.externalId(), "externalId");
        assertEquals("meinerlp", link.displayName(), "displayName");
        assertTrue(link.verified(), "verified");
        assertEquals(START, link.linkedAt(), "linkedAt");
    }

    @Test
    void redeemAcceptsLowercaseCodeWithoutDash() {
        String code = issue("discord");

        redeem(code.toLowerCase().replace("-", ""), "discord", DISCORD_ACCOUNT);

        assertEquals(1, service.list(player).size(), "linked");
    }

    @Test
    void aCodeCanBeRedeemedOnlyOnce() {
        String code = issue("discord");
        redeem(code, "discord", DISCORD_ACCOUNT);

        assertThrows(LinkCodeInvalidProblem.class, () -> redeem(code, "discord", "999"));
    }

    @Test
    void aCodeIsStillValidAtTenMinutesAndInvalidAfter() {
        String code = issue("discord");
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        assertThrows(LinkCodeInvalidProblem.class, () -> redeem(code, "discord", DISCORD_ACCOUNT));
    }

    @Test
    void aCodeOfAnotherProviderIsInvalidAndStaysRedeemable() {
        String code = issue("twitch");

        assertThrows(LinkCodeInvalidProblem.class, () -> redeem(code, "discord", DISCORD_ACCOUNT), "wrong provider");
        redeem(code, "twitch", "twitch-42");
        assertEquals("twitch", service.list(player).getFirst().provider(), "still redeemable for its provider");
    }

    @Test
    void anUnknownOrMalformedCodeIsInvalid() {
        assertThrows(LinkCodeInvalidProblem.class, () -> redeem("ZZZZ-ZZZZ", "discord", DISCORD_ACCOUNT), "unknown");
        assertThrows(LinkCodeInvalidProblem.class, () -> redeem("not a code", "discord", DISCORD_ACCOUNT), "malformed");
        assertThrows(LinkCodeInvalidProblem.class, () -> redeem(null, "discord", DISCORD_ACCOUNT), "missing");
    }

    @Test
    void anExternalAccountOfAnotherPlayerIsAConflict() {
        UUID other = UUID.randomUUID();
        repositories.addPlayer(other);
        String otherCode = service.issueCode(other, "discord").code();
        redeem(otherCode, "discord", DISCORD_ACCOUNT);
        String code = issue("discord");

        assertThrows(ExternalAccountAlreadyLinkedProblem.class, () -> redeem(code, "discord", DISCORD_ACCOUNT));

        assertEquals(other, service.lookup("discord", DISCORD_ACCOUNT).playerUuid(), "the other player keeps the link");
    }

    @Test
    void aPlayerWithAVerifiedLinkCannotRedeemTheProviderAgain() {
        redeem(issue("discord"), "discord", DISCORD_ACCOUNT);
        String code = issue("discord");

        assertThrows(ProviderAlreadyLinkedProblem.class, () -> redeem(code, "discord", "another-account"));
    }

    @Test
    void relinkingAfterUnlinkSucceeds() {
        redeem(issue("discord"), "discord", DISCORD_ACCOUNT);
        service.delete(player, "discord");

        redeem(issue("discord"), "discord", DISCORD_ACCOUNT);

        assertEquals(1, service.list(player).size(), "linked again");
    }

    @Test
    void aUniqueViolationOfARacingRedeemBecomesTheMatchingConflict() {
        UUID other = UUID.randomUUID();
        UUID otherId = repositories.addPlayer(other);
        String code = issue("discord");
        repositories.links.failNextSave(new RuntimeException("insert failed", new SQLException("unique", "23505")),
                () -> repositories.links.insertDirectly(
                        new AccountLink(otherId, "discord", DISCORD_ACCOUNT, null, null, true, START)));

        assertThrows(ExternalAccountAlreadyLinkedProblem.class, () -> redeem(code, "discord", DISCORD_ACCOUNT));
    }

    @Test
    void anUnrelatedFailureDuringRedeemIsNotSwallowed() {
        String code = issue("discord");
        repositories.links.failNextSave(new IllegalStateException("database down"), () -> { });

        assertThrows(IllegalStateException.class, () -> redeem(code, "discord", DISCORD_ACCOUNT));
    }

    // --- unverified links ----------------------------------------------------------------------------------

    @Test
    void anUnverifiedLinkIsStoredAndCanBeReplaced() {
        AccountLinkDTO first = service.putUnverified(player, "youtube", "https://www.youtube.com/@onelitefeather");
        AccountLinkDTO second = service.putUnverified(player, "youtube", "@other");

        assertFalse(first.verified(), "unverified");
        assertEquals("https://www.youtube.com/@onelitefeather", first.value(), "value");
        assertEquals("@other", second.value(), "replaced value");
        assertEquals(1, service.list(player).size(), "still exactly one youtube link");
    }

    @Test
    void anInvalidUnverifiedValueIsRejected() {
        assertThrows(InvalidLinkValueProblem.class, () -> service.putUnverified(player, "twitch", "https://evil.example/twitch"));
    }

    @Test
    void aVerifiedLinkIsNotOverwrittenByAnUnverifiedValue() {
        redeem(issue("discord"), "discord", DISCORD_ACCOUNT);

        assertThrows(ProviderAlreadyLinkedProblem.class, () -> service.putUnverified(player, "discord", "meinerlp"));

        assertTrue(service.list(player).getFirst().verified(), "verified link unchanged");
    }

    @Test
    void redeemUpgradesAnUnverifiedLinkInPlace() {
        service.putUnverified(player, "twitch", "onelitefeather");

        redeem(issue("twitch"), "twitch", "twitch-42");

        List<AccountLinkDTO> links = service.list(player);
        assertEquals(1, links.size(), "exactly one twitch link");
        assertTrue(links.getFirst().verified(), "verified");
        assertEquals("twitch-42", links.getFirst().externalId(), "externalId");
        assertEquals(null, links.getFirst().value(), "the unverified value is gone");
    }

    @Test
    void unverifiedPutForAnUnknownPlayerIsAProblem() {
        assertThrows(PlayerNotFoundProblem.class, () -> service.putUnverified(UUID.randomUUID(), "x", "onelitefeather"));
    }

    // --- list, delete, lookup ------------------------------------------------------------------------------

    @Test
    void aPlayerWithoutLinksHasAnEmptyList() {
        assertTrue(service.list(player).isEmpty(), "no links");
    }

    @Test
    void listingForAnUnknownPlayerIsAProblem() {
        assertThrows(PlayerNotFoundProblem.class, () -> service.list(UUID.randomUUID()));
    }

    @Test
    void deletingALinkIsIdempotent() {
        redeem(issue("discord"), "discord", DISCORD_ACCOUNT);

        service.delete(player, "discord");
        service.delete(player, "discord");

        assertTrue(service.list(player).isEmpty(), "unlinked");
    }

    @Test
    void deletingForAnUnknownPlayerIsAProblem() {
        assertThrows(PlayerNotFoundProblem.class, () -> service.delete(UUID.randomUUID(), "discord"));
    }

    @Test
    void lookupFindsTheOwnerOfAVerifiedLink() {
        redeem(issue("discord"), "discord", DISCORD_ACCOUNT);

        LinkLookupDTO found = service.lookup("discord", DISCORD_ACCOUNT);

        assertEquals(player, found.playerUuid(), "owner");
        assertEquals(DISCORD_ACCOUNT, found.link().externalId(), "link");
    }

    @Test
    void lookupIgnoresUnverifiedLinks() {
        service.putUnverified(player, "x", "onelitefeather");

        assertThrows(LinkNotFoundProblem.class, () -> service.lookup("x", "onelitefeather"));
    }

    @Test
    void lookupOfAnUnknownProviderIsAProblem() {
        assertThrows(UnsupportedProviderProblem.class, () -> service.lookup("myspace", "1"));
    }

    private static UUID playerUuidOf(LinkLookupDTO linked) {
        return linked.playerUuid();
    }
}
