package net.onelitefeather.otis.events;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.AccountLink;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.AccountLinkRepository;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import net.onelitefeather.otis.dto.LinkCodeDTO;
import net.onelitefeather.otis.dto.RedeemRequestDTO;
import net.onelitefeather.otis.problem.OtisProblemException;
import net.onelitefeather.otis.service.AccountLinkService;
import net.onelitefeather.otis.service.LinkTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec "Link changes produce events" and "Transactional outbox": with publishing enabled every successful link
 * change writes exactly one outbox row in the transaction of the change. A fake publisher bean keeps Kafka out.
 */
@MicronautTest(environments = "test", transactional = false)
@Property(name = "otis.events.enabled", value = "true")
@Property(name = "otis.events.publisher", value = "fake")
@Property(name = "otis.events.relay.initial-delay", value = "1h")
class AccountLinkOutboxTest {

    private static final String DISCORD_ACCOUNT = "123456789012345678";
    private static final String DISPLAY_NAME = "secret-display-name";

    @Inject
    AccountLinkService service;

    @Inject
    LinkTransactions transactions;

    @Inject
    OutboxWriter writer;

    @Inject
    AccountLinkRepository links;

    @Inject
    OtisPlayerRepository players;

    @Inject
    DataSource dataSource;

    private final List<UUID> createdPlayers = new ArrayList<>();

    @AfterEach
    void removeOwnFixtures() {
        createdPlayers.forEach(players::deleteById);
    }

    private UUID storePlayer() {
        UUID mojangUuid = UUID.randomUUID();
        OtisPlayer saved = players.save(new OtisPlayer(null, mojangUuid,
                "O" + mojangUuid.toString().substring(0, 8).replace('-', '_'), 1000L, 2000L, Map.of(), Locale.US));
        createdPlayers.add(saved.getUuid());
        return mojangUuid;
    }

    private List<OutboxRows.Row> rows(UUID player) {
        return OutboxRows.of(dataSource, player);
    }

    private void redeem(UUID player, String provider, String externalId) {
        LinkCodeDTO code = service.issueCode(player, provider);
        service.redeem(new RedeemRequestDTO(code.code(), provider, externalId, DISPLAY_NAME));
    }

    @Test
    void redeemWritesOneLinkedRow() {
        UUID player = storePlayer();

        redeem(player, "discord", DISCORD_ACCOUNT);

        List<OutboxRows.Row> rows = rows(player);
        assertEquals(1, rows.size(), "exactly one event row");
        assertEquals("net.onelitefeather.otis.account.linked", rows.getFirst().type(), "event type");
        assertEquals(player.toString(), rows.getFirst().key(), "the Kafka key is the player uuid");
        assertTrue(rows.getFirst().payload().startsWith("{"), "the payload is stored as a JSON object: " + rows.getFirst().payload());
        assertTrue(rows.getFirst().payload().contains("\"externalId\":\"" + DISCORD_ACCOUNT + "\""),
                "the linked event of a verified link carries the externalId: " + rows.getFirst().payload());
    }

    @Test
    void theStoredEventNeverContainsTheCodeOrTheDisplayName() {
        UUID player = storePlayer();
        LinkCodeDTO code = service.issueCode(player, "discord");

        service.redeem(new RedeemRequestDTO(code.code(), "discord", DISCORD_ACCOUNT, DISPLAY_NAME));

        String payload = rows(player).getFirst().payload();
        assertFalse(payload.contains(code.code()) || payload.contains(code.code().replace("-", "")), "no link code: " + payload);
        assertFalse(payload.contains(DISPLAY_NAME), "no display name: " + payload);
    }

    @Test
    void settingAnUnverifiedLinkWritesALinkedRowWithoutExternalIdAndValue() {
        UUID player = storePlayer();

        service.putUnverified(player, "github", "secrethandle42");

        List<OutboxRows.Row> rows = rows(player);
        assertEquals(1, rows.size(), "exactly one event row");
        assertEquals("net.onelitefeather.otis.account.linked", rows.getFirst().type(), "event type");
        assertTrue(rows.getFirst().payload().contains("\"verified\":false"), "unverified: " + rows.getFirst().payload());
        assertTrue(rows.getFirst().payload().contains("\"externalId\":null"), "externalId null: " + rows.getFirst().payload());
        assertFalse(rows.getFirst().payload().contains("secrethandle42"), "the link value is not published");
    }

    @Test
    void upgradingAnUnverifiedLinkWritesASecondLinkedRow() {
        UUID player = storePlayer();
        service.putUnverified(player, "twitch", "someStreamer");

        redeem(player, "twitch", "98765");

        List<OutboxRows.Row> rows = rows(player);
        assertEquals(2, rows.size(), "one row for the unverified set, one for the upgrade");
        assertTrue(rows.stream().allMatch(row -> row.type().endsWith(".linked")), "both are linked events");
        assertEquals(1, rows.stream().filter(row -> row.payload().contains("\"verified\":true")).count(),
                "exactly one of them is the verified upgrade");
    }

    @Test
    void unlinkingWritesOneUnlinkedRowWithTheRemovedLinksExternalId() {
        UUID player = storePlayer();
        redeem(player, "discord", DISCORD_ACCOUNT);

        service.delete(player, "discord");

        List<OutboxRows.Row> rows = rows(player);
        assertEquals(2, rows.size(), "linked and unlinked");
        OutboxRows.Row unlinked = rows.stream().filter(row -> row.type().endsWith(".unlinked")).findFirst().orElseThrow();
        assertTrue(unlinked.payload().contains("\"externalId\":\"" + DISCORD_ACCOUNT + "\""), unlinked.payload());
        assertEquals(0, links.findAllByPlayer(links.findPlayerIds(player).getFirst()).size(), "the link is gone");
    }

    @Test
    void deletingAMissingLinkWritesNoRow() {
        UUID player = storePlayer();

        service.delete(player, "twitch");

        assertEquals(List.of(), rows(player), "a no-op delete produces no event");
    }

    @Test
    void aRejectedRedeemWritesNoRowForTheLoser() {
        UUID owner = storePlayer();
        UUID other = storePlayer();
        redeem(owner, "discord", DISCORD_ACCOUNT);
        LinkCodeDTO code = service.issueCode(other, "discord");

        assertThrows(OtisProblemException.class,
                () -> service.redeem(new RedeemRequestDTO(code.code(), "discord", DISCORD_ACCOUNT, DISPLAY_NAME)));

        assertEquals(List.of(), rows(other), "the 409 produces no event");
        assertEquals(1, rows(owner).size(), "the owner's event is unaffected");
    }

    @Test
    void anExceptionAfterTheOutboxWriteRollsBackTheLinkAndTheRow() {
        UUID player = storePlayer();
        UUID playerId = links.findPlayerIds(player).getFirst();

        assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
            links.save(new AccountLink(playerId, "discord", DISCORD_ACCOUNT, null, null, true, Instant.now()));
            writer.linked(player, "discord", DISCORD_ACCOUNT, true, Instant.now());
            throw new IllegalStateException("failure after the outbox write");
        }));

        assertEquals(List.of(), rows(player), "the outbox row is rolled back with the change");
        assertTrue(links.findByPlayerIdAndProvider(playerId, "discord").isEmpty(), "the link is rolled back too");
    }
}
