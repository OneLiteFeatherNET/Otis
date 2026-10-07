package net.onelitefeather.otis.service;

import net.onelitefeather.otis.database.entity.PlayerSetting;
import net.onelitefeather.otis.database.repository.PlayerSettingRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory stand-in for the repository. Every test builds its own instance, so nothing is shared.
 */
final class FakePlayerSettingRepository implements PlayerSettingRepository {

    private final Map<UUID, UUID> playerIdsByMojangUuid = new HashMap<>();
    private final List<PlayerSetting> settings = new ArrayList<>();
    private RuntimeException failNextSave;
    private Runnable beforeFailingSave = () -> { };

    /** Registers a player and returns the primary key the fake assigned to it. */
    UUID addPlayer(UUID mojangUuid) {
        UUID id = UUID.randomUUID();
        playerIdsByMojangUuid.put(mojangUuid, id);
        return id;
    }

    /** Makes the next {@code save} run {@code sideEffect} (e.g. a competing insert) and then throw. */
    void failNextSave(RuntimeException failure, Runnable sideEffect) {
        this.failNextSave = failure;
        this.beforeFailingSave = sideEffect;
    }

    /** Stores a setting directly, as a competing writer would. */
    void insertDirectly(PlayerSetting setting) {
        assignId(setting);
        settings.add(setting);
    }

    List<PlayerSetting> all() {
        return List.copyOf(settings);
    }

    @Override
    public List<UUID> findPlayerIds(UUID playerUuid) {
        return Optional.ofNullable(playerIdsByMojangUuid.get(playerUuid)).map(List::of).orElse(List.of());
    }

    @Override
    public Optional<PlayerSetting> findByPlayerIdAndKeyNamespaceAndKeyValue(
            UUID playerId, String keyNamespace, String keyValue) {
        return settings.stream()
                .filter(s -> s.getPlayerId().equals(playerId)
                        && s.getKeyNamespace().equals(keyNamespace) && s.getKeyValue().equals(keyValue))
                .findFirst();
    }

    @Override
    public List<PlayerSetting> findAllByPlayer(UUID playerId) {
        return settings.stream().filter(s -> s.getPlayerId().equals(playerId)).sorted(ORDER).toList();
    }

    @Override
    public List<PlayerSetting> findAllByPlayerAndNamespaces(
            UUID playerId, Collection<String> keyNamespaces) {
        return settings.stream()
                .filter(s -> s.getPlayerId().equals(playerId) && keyNamespaces.contains(s.getKeyNamespace()))
                .sorted(ORDER).toList();
    }

    @Override
    public PlayerSetting save(PlayerSetting setting) {
        if (failNextSave != null) {
            RuntimeException failure = failNextSave;
            failNextSave = null;
            beforeFailingSave.run();
            throw failure;
        }
        assignId(setting);
        settings.add(setting);
        return setting;
    }

    @Override
    public PlayerSetting update(PlayerSetting setting) {
        return setting; // entities are mutated in place, like managed entities
    }

    @Override
    public long deleteByPlayerIdAndKeyNamespaceAndKeyValue(UUID playerId, String keyNamespace, String keyValue) {
        return settings.removeIf(s -> s.getPlayerId().equals(playerId)
                && s.getKeyNamespace().equals(keyNamespace) && s.getKeyValue().equals(keyValue)) ? 1 : 0;
    }

    private static final Comparator<PlayerSetting> ORDER =
            Comparator.comparing(PlayerSetting::getKeyNamespace).thenComparing(PlayerSetting::getKeyValue);

    private static void assignId(PlayerSetting setting) {
        try {
            Field id = PlayerSetting.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(setting, UUID.randomUUID());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
