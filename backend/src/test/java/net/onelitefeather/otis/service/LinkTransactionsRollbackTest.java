package net.onelitefeather.otis.service;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.otis.database.entity.LinkCode;
import net.onelitefeather.otis.database.entity.OtisPlayer;
import net.onelitefeather.otis.database.repository.LinkCodeRepository;
import net.onelitefeather.otis.database.repository.OtisPlayerRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The declarative {@code @Transactional} is ambiguous in this application (two primary transaction
 * managers), so {@link LinkTransactions} must pick the Hibernate one explicitly. This proves that the
 * repository calls really run inside that transaction: a failure after the code claim undoes the claim.
 */
@MicronautTest(environments = "test", transactional = false)
class LinkTransactionsRollbackTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Inject
    LinkTransactions transactions;

    @Inject
    LinkCodeRepository codes;

    @Inject
    OtisPlayerRepository players;

    private String storeOpenCode(String hash) {
        OtisPlayer player = players.save(new OtisPlayer(
                null, UUID.randomUUID(), "Tx_Player", 1L, 2L, Map.of(), Locale.US));
        codes.save(new LinkCode(player.getUuid(), "discord", hash, NOW, NOW.plusSeconds(600)));
        return hash;
    }

    @Test
    void aFailureAfterTheClaimLeavesTheCodeUnconsumed() {
        String hash = storeOpenCode("a".repeat(64));

        assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
            assertEquals(1, codes.claim(hash, "discord", NOW.plusSeconds(1)), "the claim itself succeeds");
            throw new IllegalStateException("conflict detected after the claim");
        }));

        assertEquals(1, codes.claim(hash, "discord", NOW.plusSeconds(2)),
                "the rolled back claim must leave the code redeemable");
    }

    @Test
    void aSuccessfulTransactionKeepsTheClaim() {
        String hash = storeOpenCode("b".repeat(64));

        int claimed = transactions.execute(() -> codes.claim(hash, "discord", NOW.plusSeconds(1)));

        assertEquals(1, claimed, "the claim succeeds");
        assertEquals(0, codes.claim(hash, "discord", NOW.plusSeconds(2)), "a committed claim consumes the code");
    }
}
