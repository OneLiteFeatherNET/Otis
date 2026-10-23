package net.onelitefeather.otis.database.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import net.onelitefeather.otis.database.entity.LinkCode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of {@link LinkCode}. A {@link GenericRepository} with only the operations the service needs,
 * so the service can be tested against a small in-memory fake.
 */
@Repository
public interface LinkCodeRepository extends GenericRepository<LinkCode, UUID> {

    LinkCode save(LinkCode code);

    Optional<LinkCode> findByCodeHash(String codeHash);

    /**
     * Atomically consumes a code: only one caller can win, and only while the code is open, unexpired and
     * issued for {@code provider}.
     *
     * @return the number of consumed codes (1 when claimed, 0 otherwise)
     */
    @Query("UPDATE LinkCode code SET code.usedAt = :now WHERE code.codeHash = :codeHash AND code.provider = :provider "
            + "AND code.usedAt IS NULL AND code.revokedAt IS NULL AND code.expiresAt > :now")
    int claim(String codeHash, String provider, Instant now);

    /** Revokes the open codes of a player and provider; returns how many were revoked. */
    @Query("UPDATE LinkCode code SET code.revokedAt = :now WHERE code.playerId = :playerId AND code.provider = :provider "
            + "AND code.usedAt IS NULL AND code.revokedAt IS NULL AND code.expiresAt > :now")
    int revokeOpen(UUID playerId, String provider, Instant now);

    /** @return how many codes the player was issued after {@code after} (any provider, any state) */
    @Query("SELECT COUNT(code) FROM LinkCode code WHERE code.playerId = :playerId AND code.createdAt > :after")
    long countIssuedAfter(UUID playerId, Instant after);

    /** Deletes the player's codes created before {@code before}; returns how many were deleted. */
    @Query("DELETE FROM LinkCode code WHERE code.playerId = :playerId AND code.createdAt < :before")
    int deleteCreatedBefore(UUID playerId, Instant before);
}
