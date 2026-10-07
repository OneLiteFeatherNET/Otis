package net.onelitefeather.otis.database.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import net.onelitefeather.otis.database.entity.PlayerSetting;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of {@link PlayerSetting}. Deliberately a {@link GenericRepository} with only the
 * operations the service needs, so the service can be tested against a small in-memory fake.
 */
@Repository
public interface PlayerSettingRepository extends GenericRepository<PlayerSetting, UUID> {

    /**
     * @param playerUuid the Mojang uuid of the player
     * @return the primary keys of the stored player rows for that uuid (normally exactly one; the
     * column is not unique), ordered for a deterministic pick
     */
    @Query("SELECT player.uuid FROM OtisPlayer player WHERE player.playerUuid = :playerUuid ORDER BY player.uuid")
    List<UUID> findPlayerIds(UUID playerUuid);

    Optional<PlayerSetting> findByPlayerIdAndKeyNamespaceAndKeyValue(UUID playerId, String keyNamespace, String keyValue);

    @Query("SELECT setting FROM PlayerSetting setting WHERE setting.playerId = :playerId "
            + "ORDER BY setting.keyNamespace, setting.keyValue")
    List<PlayerSetting> findAllByPlayer(UUID playerId);

    @Query("SELECT setting FROM PlayerSetting setting WHERE setting.playerId = :playerId "
            + "AND setting.keyNamespace IN (:keyNamespaces) ORDER BY setting.keyNamespace, setting.keyValue")
    List<PlayerSetting> findAllByPlayerAndNamespaces(UUID playerId, Collection<String> keyNamespaces);

    PlayerSetting save(PlayerSetting setting);

    PlayerSetting update(PlayerSetting setting);

    /**
     * @return the number of deleted rows (0 or 1)
     */
    long deleteByPlayerIdAndKeyNamespaceAndKeyValue(UUID playerId, String keyNamespace, String keyValue);
}
