package net.onelitefeather.otis.database.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import net.onelitefeather.otis.database.entity.AccountLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of {@link AccountLink}. A {@link GenericRepository} with only the operations the service
 * needs, so the service can be tested against a small in-memory fake.
 */
@Repository
public interface AccountLinkRepository extends GenericRepository<AccountLink, UUID> {

    /**
     * @param playerUuid the Mojang uuid of the player
     * @return the primary keys of the stored player rows for that uuid (normally exactly one; the
     * column is not unique), ordered for a deterministic pick
     */
    @Query("SELECT player.uuid FROM OtisPlayer player WHERE player.playerUuid = :playerUuid ORDER BY player.uuid")
    List<UUID> findPlayerIds(UUID playerUuid);

    /** @return the Mojang uuid of the player with primary key {@code playerId} */
    @Query("SELECT player.playerUuid FROM OtisPlayer player WHERE player.uuid = :playerId")
    Optional<UUID> findMojangUuid(UUID playerId);

    AccountLink save(AccountLink link);

    AccountLink update(AccountLink link);

    Optional<AccountLink> findByPlayerIdAndProvider(UUID playerId, String provider);

    /** @return the verified link of the external account, if any; unverified links never match */
    @Query("SELECT link FROM AccountLink link WHERE link.provider = :provider AND link.externalId = :externalId "
            + "AND link.verified = true")
    Optional<AccountLink> findVerified(String provider, String externalId);

    @Query("SELECT link FROM AccountLink link WHERE link.playerId = :playerId ORDER BY link.provider")
    List<AccountLink> findAllByPlayer(UUID playerId);

    /** @return the number of deleted rows (0 or 1) */
    long deleteByPlayerIdAndProvider(UUID playerId, String provider);
}
