package net.onelitefeather.otis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import net.kyori.adventure.key.Key;
import net.onelitefeather.otis.database.entity.PlayerSetting;
import net.onelitefeather.otis.database.repository.PlayerSettingRepository;
import net.onelitefeather.otis.dto.PlayerSettingDTO;
import net.onelitefeather.otis.problem.PlayerNotFoundProblem;
import net.onelitefeather.otis.problem.SettingNotFoundProblem;
import net.onelitefeather.otis.problem.SettingValueTooLargeProblem;
import net.onelitefeather.otis.settings.SettingJson;
import net.onelitefeather.otis.settings.SettingKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The rules of player settings: key validation, bounded values, idempotent last-write-wins upserts and
 * one internal span per use case. Setting values are stored opaquely and never logged or traced.
 */
@Singleton
public class PlayerSettingService {

    /** Largest accepted serialized setting value in bytes (64 KiB). */
    public static final int MAX_VALUE_BYTES = 65536;

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSettingService.class);

    private final PlayerSettingRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SettingSpans spans;

    @Inject
    public PlayerSettingService(PlayerSettingRepository repository, @Named(SettingJson.MAPPER_NAME) ObjectMapper mapper, Clock clock,
                                OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.spans = new SettingSpans(openTelemetry);
    }

    /**
     * @param playerUuid the Mojang uuid of the player
     * @param namespaces only settings of these namespaces; empty for all settings
     * @return the matching settings sorted by key
     * @throws PlayerNotFoundProblem if the player is not stored in Otis
     */
    public List<PlayerSettingDTO> list(UUID playerUuid, Collection<String> namespaces) {
        return spans.inSpan("settings.list", playerUuid, observation -> {
            observation.namespaces(namespaces);
            UUID playerId = resolvePlayer(playerUuid);
            List<PlayerSetting> found = namespaces.isEmpty()
                    ? repository.findAllByPlayer(playerId)
                    : repository.findAllByPlayerAndNamespaces(playerId, namespaces);
            observation.outcome(SettingSpans.FOUND);
            LOGGER.atDebug().addKeyValue("operation", "list").addKeyValue("outcome", SettingSpans.FOUND)
                    .addKeyValue("count", found.size()).log("settings operation finished");
            return found.stream().map(this::toDto).toList();
        });
    }

    /**
     * @param playerUuid the Mojang uuid of the player
     * @param rawKey     the setting key as {@code namespace:value}
     * @return the stored setting
     * @throws PlayerNotFoundProblem  if the player is not stored in Otis
     * @throws SettingNotFoundProblem if the player has no such setting
     */
    public PlayerSettingDTO get(UUID playerUuid, String rawKey) {
        return spans.inSpan("settings.get", playerUuid, observation -> {
            Key key = parseKey(observation, rawKey);
            UUID playerId = resolvePlayer(playerUuid);
            PlayerSetting setting = find(playerId, key).orElseThrow(SettingNotFoundProblem::new);
            observation.outcome(SettingSpans.FOUND);
            logOutcome("get", key, SettingSpans.FOUND);
            return toDto(setting);
        });
    }

    /**
     * Stores {@code value} under {@code rawKey}: creates the setting, replaces a different value, or does
     * nothing when the stored value is semantically equal. Concurrent first writes end in one row.
     *
     * @param playerUuid the Mojang uuid of the player
     * @param rawKey     the setting key as {@code namespace:value}
     * @param value      any JSON value
     * @return what happened, with the setting as stored afterwards
     * @throws SettingValueTooLargeProblem if the serialized value exceeds {@link #MAX_VALUE_BYTES}
     * @throws PlayerNotFoundProblem       if the player is not stored in Otis
     */
    public PutResult put(UUID playerUuid, String rawKey, JsonNode value) {
        return spans.inSpan("settings.put", playerUuid, observation -> {
            Key key = parseKey(observation, rawKey);
            requireBoundedSize(value);
            UUID playerId = resolvePlayer(playerUuid);
            PutResult result;
            try {
                result = upsert(playerId, key, value);
            } catch (RuntimeException failure) {
                if (!isUniqueViolation(failure)) {
                    throw failure;
                }
                // a concurrent first write won the race; the row exists now, so this attempt updates or no-ops
                LOGGER.atDebug().addKeyValue("operation", "put").addKeyValue("key", key.asString())
                        .log("concurrent first write detected, retrying once");
                result = upsert(playerId, key, value);
            }
            String outcome = switch (result) {
                case PutResult.Created _ -> SettingSpans.CREATED;
                case PutResult.Updated _ -> SettingSpans.UPDATED;
                case PutResult.Unchanged _ -> SettingSpans.UNCHANGED;
            };
            observation.outcome(outcome);
            logOutcome("put", key, outcome);
            return result;
        });
    }

    /**
     * @param playerUuid the Mojang uuid of the player
     * @param rawKey     the setting key as {@code namespace:value}
     * @return {@code true} if a setting was removed, {@code false} if there was none (not an error)
     * @throws PlayerNotFoundProblem if the player is not stored in Otis
     */
    public boolean delete(UUID playerUuid, String rawKey) {
        return spans.inSpan("settings.delete", playerUuid, observation -> {
            Key key = parseKey(observation, rawKey);
            UUID playerId = resolvePlayer(playerUuid);
            boolean deleted = repository.deleteByPlayerIdAndKeyNamespaceAndKeyValue(
                    playerId, key.namespace(), key.value()) > 0;
            String outcome = deleted ? SettingSpans.DELETED : SettingSpans.NOT_FOUND;
            observation.outcome(outcome);
            logOutcome("delete", key, outcome);
            return deleted;
        });
    }

    /**
     * One attempt of the upsert. Deliberately without a surrounding transaction: with both Spring and
     * Micronaut transaction managers on the classpath the declarative {@code @Transactional} is ambiguous,
     * and each repository call is atomic on its own. Concurrent writers are resolved by the unique
     * constraint (see {@link #put}); a concurrent update of the same setting is last-write-wins.
     */
    private PutResult upsert(UUID playerId, Key key, JsonNode value) {
        Optional<PlayerSetting> existing = find(playerId, key);
        if (existing.isEmpty()) {
            PlayerSetting created = repository.save(new PlayerSetting(playerId, key, value, 1, now()));
            return new PutResult.Created(toDto(created));
        }
        PlayerSetting setting = existing.get();
        if (value.equals(setting.getValue())) {
            return new PutResult.Unchanged(toDto(setting));
        }
        setting.setValue(value);
        setting.setVersion(setting.getVersion() + 1);
        setting.setUpdatedAt(now());
        return new PutResult.Updated(toDto(repository.update(setting)));
    }

    private Key parseKey(SettingSpans.Observation observation, String rawKey) {
        Key key = SettingKeys.parse(rawKey);
        observation.key(key);
        return key;
    }

    private UUID resolvePlayer(UUID playerUuid) {
        return repository.findPlayerIds(playerUuid).stream().findFirst().orElseThrow(PlayerNotFoundProblem::new);
    }

    private Optional<PlayerSetting> find(UUID playerId, Key key) {
        return repository.findByPlayerIdAndKeyNamespaceAndKeyValue(playerId, key.namespace(), key.value());
    }

    private void requireBoundedSize(JsonNode value) {
        try {
            if (mapper.writeValueAsBytes(value).length > MAX_VALUE_BYTES) {
                throw new SettingValueTooLargeProblem(MAX_VALUE_BYTES);
            }
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private PlayerSettingDTO toDto(PlayerSetting setting) {
        return PlayerSettingDTO.of(setting, mapper.convertValue(setting.getValue(), Object.class));
    }

    /** Microsecond precision, so the value returned by a write equals the value read back from the database. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void logOutcome(String operation, Key key, String outcome) {
        LOGGER.atDebug().addKeyValue("operation", operation).addKeyValue("namespace", key.namespace())
                .addKeyValue("key", key.asString()).addKeyValue("outcome", outcome)
                .log("settings operation finished");
    }

    /** SQLSTATE class 23 is "integrity constraint violation" for PostgreSQL, MariaDB and H2 alike. */
    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
