package net.onelitefeather.otis.service;

import net.onelitefeather.otis.database.entity.AccountLink;
import net.onelitefeather.otis.database.entity.LinkCode;
import net.onelitefeather.otis.database.repository.AccountLinkRepository;
import net.onelitefeather.otis.database.repository.LinkCodeRepository;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory stand-ins for the link repositories, including their unique constraints and the semantics of
 * the conditional code claim. Every test builds its own instances, so nothing is shared.
 */
final class FakeLinkRepositories {

    final Links links = new Links();
    final Codes codes = new Codes();

    /** Registers a player and returns the primary key the fake assigned to it. */
    UUID addPlayer(UUID mojangUuid) {
        UUID id = UUID.randomUUID();
        links.playerIdsByMojangUuid.put(mojangUuid, id);
        return id;
    }

    private static void assignId(Object entity) {
        try {
            Field id = entity.getClass().getDeclaredField("id");
            id.setAccessible(true);
            id.set(entity, UUID.randomUUID());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RuntimeException uniqueViolation() {
        return new RuntimeException("insert failed", new SQLException("unique constraint violated", "23505"));
    }

    static final class Links implements AccountLinkRepository {

        private final Map<UUID, UUID> playerIdsByMojangUuid = new HashMap<>();
        private final List<AccountLink> rows = new ArrayList<>();
        private RuntimeException failNextSave;
        private Runnable beforeFailingSave = () -> { };

        /** Makes the next {@code save} run {@code sideEffect} (e.g. a competing insert) and then throw. */
        void failNextSave(RuntimeException failure, Runnable sideEffect) {
            this.failNextSave = failure;
            this.beforeFailingSave = sideEffect;
        }

        /** Stores a link directly, as a competing writer would. */
        void insertDirectly(AccountLink link) {
            assignId(link);
            rows.add(link);
        }

        List<AccountLink> all() {
            return List.copyOf(rows);
        }

        @Override
        public List<UUID> findPlayerIds(UUID playerUuid) {
            return Optional.ofNullable(playerIdsByMojangUuid.get(playerUuid)).map(List::of).orElse(List.of());
        }

        @Override
        public Optional<UUID> findMojangUuid(UUID playerId) {
            return playerIdsByMojangUuid.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(playerId)).map(Map.Entry::getKey).findFirst();
        }

        @Override
        public AccountLink save(AccountLink link) {
            if (failNextSave != null) {
                RuntimeException failure = failNextSave;
                failNextSave = null;
                beforeFailingSave.run();
                throw failure;
            }
            boolean duplicate = rows.stream().anyMatch(existing ->
                    (existing.getPlayerId().equals(link.getPlayerId()) && existing.getProvider().equals(link.getProvider()))
                            || (link.getExternalId() != null && existing.getProvider().equals(link.getProvider())
                            && link.getExternalId().equals(existing.getExternalId())));
            if (duplicate) {
                throw uniqueViolation();
            }
            assignId(link);
            rows.add(link);
            return link;
        }

        @Override
        public AccountLink update(AccountLink link) {
            return link; // entities are mutated in place, like managed entities
        }

        @Override
        public Optional<AccountLink> findByPlayerIdAndProvider(UUID playerId, String provider) {
            return rows.stream()
                    .filter(l -> l.getPlayerId().equals(playerId) && l.getProvider().equals(provider)).findFirst();
        }

        @Override
        public Optional<AccountLink> findVerified(String provider, String externalId) {
            return rows.stream().filter(l -> l.isVerified() && l.getProvider().equals(provider)
                    && externalId.equals(l.getExternalId())).findFirst();
        }

        @Override
        public List<AccountLink> findAllByPlayer(UUID playerId) {
            return rows.stream().filter(l -> l.getPlayerId().equals(playerId))
                    .sorted(Comparator.comparing(AccountLink::getProvider)).toList();
        }

        @Override
        public long deleteByPlayerIdAndProvider(UUID playerId, String provider) {
            return rows.removeIf(l -> l.getPlayerId().equals(playerId) && l.getProvider().equals(provider)) ? 1 : 0;
        }
    }

    static final class Codes implements LinkCodeRepository {

        private final List<LinkCode> rows = new ArrayList<>();

        List<LinkCode> all() {
            return List.copyOf(rows);
        }

        @Override
        public LinkCode save(LinkCode code) {
            assignId(code);
            rows.add(code);
            return code;
        }

        @Override
        public Optional<LinkCode> findByCodeHash(String codeHash) {
            return rows.stream().filter(c -> c.getCodeHash().equals(codeHash)).findFirst();
        }

        @Override
        public int claim(String codeHash, String provider, Instant now) {
            Optional<LinkCode> open = rows.stream()
                    .filter(c -> c.getCodeHash().equals(codeHash) && c.getProvider().equals(provider)
                            && c.getUsedAt() == null && c.getRevokedAt() == null && c.getExpiresAt().isAfter(now))
                    .findFirst();
            open.ifPresent(c -> set(c, "usedAt", now));
            return open.isPresent() ? 1 : 0;
        }

        @Override
        public int revokeOpen(UUID playerId, String provider, Instant now) {
            List<LinkCode> open = rows.stream()
                    .filter(c -> c.getPlayerId().equals(playerId) && c.getProvider().equals(provider)
                            && c.getUsedAt() == null && c.getRevokedAt() == null && c.getExpiresAt().isAfter(now))
                    .toList();
            open.forEach(c -> set(c, "revokedAt", now));
            return open.size();
        }

        @Override
        public long countIssuedAfter(UUID playerId, Instant after) {
            return rows.stream().filter(c -> c.getPlayerId().equals(playerId) && c.getCreatedAt().isAfter(after)).count();
        }

        @Override
        public int deleteCreatedBefore(UUID playerId, Instant before) {
            int before0 = rows.size();
            rows.removeIf(c -> c.getPlayerId().equals(playerId) && c.getCreatedAt().isBefore(before));
            return before0 - rows.size();
        }

        private static void set(LinkCode code, String field, Instant value) {
            try {
                Field f = LinkCode.class.getDeclaredField(field);
                f.setAccessible(true);
                f.set(code, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
