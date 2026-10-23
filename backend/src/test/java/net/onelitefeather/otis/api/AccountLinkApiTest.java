package net.onelitefeather.otis.api;

import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Property;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * One test per scenario of the account-links spec, against the real HTTP stack and an in-memory database.
 * Every test creates its own players with random uuids and resets the clock, so tests are order independent.
 */
@MicronautTest(environments = "test", transactional = false)
@Property(name = "otis.test.clock", value = "true")
class AccountLinkApiTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final String PROBLEMS = "https://otis.onelitefeather.net/problems/";
    private static final String CODES = "/v1/players/{player}/link-codes";
    private static final String LINKS = "/v1/players/{player}/links";
    private static final String LINK = LINKS + "/{provider}";
    private static final String REDEEM = "/v1/link-codes/redeem";
    private static final String LOOKUP = "/v1/links/{provider}/{externalId}";
    private static final String DISCORD_ACCOUNT = "123456789012345678";

    @Inject
    EmbeddedServer server;

    @Inject
    OtisPlayerRepository players;

    @Inject
    DataSource dataSource;

    @Inject
    TestClock clock;

    private final List<UUID> createdPlayers = new ArrayList<>();

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    @AfterEach
    void removeOwnFixtures() {
        createdPlayers.forEach(players::deleteById);
    }

    private UUID storePlayer() {
        UUID mojangUuid = UUID.randomUUID();
        OtisPlayer saved = players.save(new OtisPlayer(
                null, mojangUuid, "L" + mojangUuid.toString().substring(0, 8).replace('-', '_'),
                1000L, 2000L, Map.of("skin", "abc"), Locale.US));
        createdPlayers.add(saved.getUuid());
        return mojangUuid;
    }

    /** A new request specification per call: RestAssured specifications accumulate state. */
    private RequestSpecification http() {
        return RestAssured.given().baseUri("http://localhost").port(server.getPort()).contentType(ContentType.JSON);
    }

    private ValidatableResponse issueResponse(UUID player, String provider) {
        return http().body(Map.of("provider", provider)).when().post(CODES, player).then();
    }

    private String issue(UUID player, String provider) {
        return issueResponse(player, provider).statusCode(201).extract().path("code");
    }

    private ValidatableResponse redeemResponse(String code, String provider, String externalId) {
        return http().body(Map.of("code", code, "provider", provider, "externalId", externalId, "displayName", "meinerlp"))
                .when().post(REDEEM).then();
    }

    private void redeemOk(String code, String provider, String externalId) {
        redeemResponse(code, provider, externalId).statusCode(201);
    }

    private ValidatableResponse links(UUID player) {
        return http().when().get(LINKS, player).then();
    }

    private static void assertProblem(ValidatableResponse response, int status, String slug) {
        response.statusCode(status).contentType("application/problem+json")
                .body("type", equalTo(PROBLEMS + slug)).body("status", equalTo(status));
    }

    // --- supported providers -------------------------------------------------------------------------------

    @Test
    void unknownProviderIsRejected() {
        assertProblem(issueResponse(storePlayer(), "myspace"), 400, "unsupported-provider");
    }

    @Test
    void providerNamesAreLowercase() {
        UUID player = storePlayer();
        redeemOk(issue(player, "discord"), "discord", DISCORD_ACCOUNT);

        links(player).statusCode(200).body("provider", equalTo(List.of("discord")));
    }

    // --- issuing -------------------------------------------------------------------------------------------

    @Test
    void codeIsIssuedWithFormatAndTenMinuteExpiry() {
        UUID player = storePlayer();

        issueResponse(player, "discord").statusCode(201)
                .body("code", matchesPattern("^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$"))
                .body("provider", equalTo("discord"))
                .body("expiresAt", equalTo(START.plus(Duration.ofMinutes(10)).toString()));
    }

    @Test
    void issuingForAnUnknownPlayerIsNotFound() {
        assertProblem(issueResponse(UUID.randomUUID(), "discord"), 404, "player-not-found");
    }

    @Test
    void issuingWithoutAProviderIsABadRequest() {
        http().body("{}").when().post(CODES, storePlayer()).then().statusCode(400)
                .contentType("application/problem+json");
    }

    @Test
    void codeIsNotStoredInPlainText() throws Exception {
        UUID player = storePlayer();
        String code = issue(player, "discord");

        String stored = String.join("|", linkCodeRows());

        assertFalse(stored.contains(code.replace("-", "")), "no column may hold the code without dash");
        assertFalse(stored.contains(code), "no column may hold the code");
    }

    private List<String> linkCodeRows() throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = dataSource.unwrap(HikariDataSource.class).getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from link_code")) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(rs.getString(i)).append(',');
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    @Test
    void newCodeReplacesTheOldOne() {
        UUID player = storePlayer();
        String codeA = issue(player, "discord");
        String codeB = issue(player, "discord");

        assertProblem(redeemResponse(codeA, "discord", DISCORD_ACCOUNT), 410, "link-code-invalid");
        redeemOk(codeB, "discord", DISCORD_ACCOUNT);
    }

    @Test
    void codesOfDifferentProvidersCoexist() {
        UUID player = storePlayer();
        String discord = issue(player, "discord");
        String twitch = issue(player, "twitch");

        redeemOk(discord, "discord", DISCORD_ACCOUNT);
        redeemOk(twitch, "twitch", "twitch-" + player);

        links(player).body("$", hasSize(2));
    }

    @Test
    void sixthCodeWithinAnHourIsRateLimited() {
        UUID player = storePlayer();
        for (int i = 0; i < 5; i++) {
            issue(player, "discord");
        }

        assertProblem(issueResponse(player, "discord"), 429, "link-code-rate-limited");
    }

    @Test
    void codeIsIssuedAgainWhenTheWindowPassed() {
        UUID player = storePlayer();
        for (int i = 0; i < 5; i++) {
            issue(player, "discord");
        }
        clock.advance(Duration.ofMinutes(60));

        issueResponse(player, "discord").statusCode(201);
    }

    // --- redeeming -----------------------------------------------------------------------------------------

    @Test
    void redeemCreatesAVerifiedLink() {
        UUID player = storePlayer();
        String code = issue(player, "discord");

        redeemResponse(code, "discord", DISCORD_ACCOUNT).statusCode(201)
                .body("playerUuid", equalTo(player.toString()))
                .body("link.provider", equalTo("discord"))
                .body("link.externalId", equalTo(DISCORD_ACCOUNT))
                .body("link.displayName", equalTo("meinerlp"))
                .body("link.verified", equalTo(true));
        links(player).statusCode(200)
                .body("[0].externalId", equalTo(DISCORD_ACCOUNT))
                .body("[0].displayName", equalTo("meinerlp"))
                .body("[0].verified", equalTo(true))
                .body("[0].linkedAt", equalTo(START.toString()));
    }

    @Test
    void redeemAcceptsLowercaseCodeWithoutDash() {
        UUID player = storePlayer();
        String code = issue(player, "discord");

        redeemOk(code.toLowerCase(Locale.ROOT).replace("-", ""), "discord", DISCORD_ACCOUNT);
    }

    @Test
    void aCodeCannotBeUsedTwice() {
        UUID player = storePlayer();
        String code = issue(player, "discord");
        redeemOk(code, "discord", DISCORD_ACCOUNT);

        assertProblem(redeemResponse(code, "discord", "999"), 410, "link-code-invalid");
    }

    @Test
    void anExpiredCodeIsGone() {
        UUID player = storePlayer();
        String code = issue(player, "discord");
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        assertProblem(redeemResponse(code, "discord", DISCORD_ACCOUNT), 410, "link-code-invalid");
    }

    @Test
    void aCodeStillWorksAtExactlyTenMinutesMinusOneSecond() {
        UUID player = storePlayer();
        String code = issue(player, "discord");
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));

        redeemOk(code, "discord", DISCORD_ACCOUNT);
    }

    @Test
    void anUnknownCodeLooksLikeAnyOtherInvalidCode() {
        assertProblem(redeemResponse("ZZZZ-ZZZZ", "discord", DISCORD_ACCOUNT), 410, "link-code-invalid");
        assertProblem(redeemResponse("garbage", "discord", DISCORD_ACCOUNT), 410, "link-code-invalid");
    }

    @Test
    void providerMismatchIsGoneAndTheCodeStaysRedeemable() {
        UUID player = storePlayer();
        String code = issue(player, "twitch");

        assertProblem(redeemResponse(code, "discord", DISCORD_ACCOUNT), 410, "link-code-invalid");
        redeemOk(code, "twitch", "twitch-" + player);
    }

    @Test
    void redeemWithoutAnExternalIdIsABadRequest() {
        UUID player = storePlayer();
        String code = issue(player, "discord");

        http().body(Map.of("code", code, "provider", "discord")).when().post(REDEEM).then().statusCode(400)
                .contentType("application/problem+json");
    }

    // --- conflicts -----------------------------------------------------------------------------------------

    @Test
    void anExternalAccountOfAnotherPlayerIsAConflictAndTheCodeStaysOpen() {
        UUID playerA = storePlayer();
        UUID playerB = storePlayer();
        redeemOk(issue(playerA, "discord"), "discord", "42");
        String codeB = issue(playerB, "discord");

        assertProblem(redeemResponse(codeB, "discord", "42"), 409, "external-account-already-linked");

        http().when().get(LOOKUP, "discord", "42").then().statusCode(200).body("playerUuid", equalTo(playerA.toString()));
        redeemOk(codeB, "discord", "43"); // the conflicting code was not consumed
    }

    @Test
    void aPlayerWithAVerifiedLinkCannotRedeemTheProviderAgain() {
        UUID player = storePlayer();
        redeemOk(issue(player, "discord"), "discord", "42");
        String code = issue(player, "discord");

        assertProblem(redeemResponse(code, "discord", "43"), 409, "provider-already-linked");

        http().when().delete(LINK, player, "discord").then().statusCode(204);
        redeemOk(code, "discord", "43"); // not consumed by the conflict
    }

    @Test
    void relinkingAfterUnlinkSucceeds() {
        UUID player = storePlayer();
        redeemOk(issue(player, "discord"), "discord", "42");
        http().when().delete(LINK, player, "discord").then().statusCode(204);

        redeemOk(issue(player, "discord"), "discord", "42");
    }

    // --- unverified links ----------------------------------------------------------------------------------

    private ValidatableResponse put(UUID player, String provider, String value) {
        return http().body(Map.of("value", value)).when().put(LINK, player, provider).then();
    }

    @Test
    void anUnverifiedLinkIsStored() {
        UUID player = storePlayer();

        put(player, "youtube", "https://www.youtube.com/@onelitefeather").statusCode(200)
                .body("verified", equalTo(false))
                .body("value", equalTo("https://www.youtube.com/@onelitefeather"));

        links(player).body("[0].provider", equalTo("youtube")).body("[0].verified", equalTo(false))
                .body("[0].value", equalTo("https://www.youtube.com/@onelitefeather"))
                .body("[0].externalId", nullValue());
    }

    @Test
    void anUnverifiedLinkIsReplaced() {
        UUID player = storePlayer();
        put(player, "x", "first_handle").statusCode(200);
        put(player, "x", "second_handle").statusCode(200);

        links(player).body("$", hasSize(1)).body("[0].value", equalTo("second_handle"));
    }

    @Test
    void aForeignDomainIsRejected() {
        assertProblem(put(storePlayer(), "twitch", "https://evil.example/twitch"), 400, "invalid-link-value");
    }

    @Test
    void aVerifiedLinkIsNotOverwritten() {
        UUID player = storePlayer();
        redeemOk(issue(player, "discord"), "discord", "42");

        assertProblem(put(player, "discord", "meinerlp"), 409, "provider-already-linked");

        links(player).body("[0].verified", equalTo(true)).body("[0].externalId", equalTo("42"));
    }

    @Test
    void redeemUpgradesAnUnverifiedLink() {
        UUID player = storePlayer();
        put(player, "twitch", "onelitefeather").statusCode(200);

        redeemOk(issue(player, "twitch"), "twitch", "twitch-42-" + player);

        links(player).body("$", hasSize(1)).body("[0].verified", equalTo(true)).body("[0].value", nullValue());
    }

    @Test
    void unverifiedPutForAnUnknownPlayerIsNotFound() {
        assertProblem(put(UUID.randomUUID(), "github", "onelitefeather"), 404, "player-not-found");
    }

    // --- list, unlink, lookup ------------------------------------------------------------------------------

    @Test
    void aPlayerWithoutLinksHasAnEmptyList() {
        links(storePlayer()).statusCode(200).body("$", empty());
    }

    @Test
    void listingForAnUnknownPlayerIsNotFound() {
        assertProblem(links(UUID.randomUUID()), 404, "player-not-found");
    }

    @Test
    void unlinkingIsIdempotent() {
        UUID player = storePlayer();
        redeemOk(issue(player, "discord"), "discord", "42");

        http().when().delete(LINK, player, "discord").then().statusCode(204);
        http().when().delete(LINK, player, "discord").then().statusCode(204);

        links(player).body("$", empty());
    }

    @Test
    void unlinkingForAnUnknownPlayerIsNotFound() {
        assertProblem(http().when().delete(LINK, UUID.randomUUID(), "discord").then(), 404, "player-not-found");
    }

    @Test
    void lookupFindsTheVerifiedLinkOfAnAccount() {
        UUID player = storePlayer();
        String account = "9" + player.toString().replace("-", "");
        redeemOk(issue(player, "discord"), "discord", account);

        http().when().get(LOOKUP, "discord", account).then().statusCode(200)
                .body("playerUuid", equalTo(player.toString()))
                .body("link.externalId", equalTo(account))
                .body("link.verified", equalTo(true));
    }

    @Test
    void lookupIgnoresUnverifiedLinks() {
        UUID player = storePlayer();
        put(player, "x", "onelitefeather").statusCode(200);

        assertProblem(http().when().get(LOOKUP, "x", "onelitefeather").then(), 404, "link-not-found");
    }

    @Test
    void lookupOfAnUnknownProviderIsABadRequest() {
        assertProblem(http().when().get(LOOKUP, "myspace", "1").then(), 400, "unsupported-provider");
    }
}
