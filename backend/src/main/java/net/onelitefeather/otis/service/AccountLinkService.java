package net.onelitefeather.otis.service;

import io.opentelemetry.api.OpenTelemetry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.onelitefeather.otis.database.entity.AccountLink;
import net.onelitefeather.otis.database.entity.LinkCode;
import net.onelitefeather.otis.database.repository.AccountLinkRepository;
import net.onelitefeather.otis.database.repository.LinkCodeRepository;
import net.onelitefeather.otis.dto.AccountLinkDTO;
import net.onelitefeather.otis.dto.LinkCodeDTO;
import net.onelitefeather.otis.dto.LinkLookupDTO;
import net.onelitefeather.otis.dto.RedeemRequestDTO;
import net.onelitefeather.otis.events.OutboxWriter;
import net.onelitefeather.otis.links.LinkCodes;
import net.onelitefeather.otis.links.Provider;
import net.onelitefeather.otis.problem.ExternalAccountAlreadyLinkedProblem;
import net.onelitefeather.otis.problem.LinkCodeInvalidProblem;
import net.onelitefeather.otis.problem.LinkCodeRateLimitedProblem;
import net.onelitefeather.otis.problem.LinkNotFoundProblem;
import net.onelitefeather.otis.problem.PlayerNotFoundProblem;
import net.onelitefeather.otis.problem.ProviderAlreadyLinkedProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The rules of account links: one-time link codes, atomic redeem, unverified profile links and one internal
 * span per use case. Link codes, external ids, display names and link values are never logged or traced.
 */
@Singleton
public class AccountLinkService {

    /** How long an issued code can be redeemed. */
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    /** Window of the issue rate limit. */
    public static final Duration RATE_WINDOW = Duration.ofMinutes(60);
    /** Codes issued per player within {@link #RATE_WINDOW}. */
    public static final int RATE_LIMIT = 5;
    /** Age after which a player's codes are deleted when the player issues a new one. */
    public static final Duration CODE_RETENTION = Duration.ofHours(24);

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountLinkService.class);

    private final AccountLinkRepository links;
    private final LinkCodeRepository codes;
    private final LinkTransactions transactions;
    private final Clock clock;
    private final LinkCodes generator;
    private final LinkSpans spans;
    private final OutboxWriter outbox;

    @Inject
    public AccountLinkService(AccountLinkRepository links, LinkCodeRepository codes, LinkTransactions transactions,
                              Clock clock, LinkCodes generator, OpenTelemetry openTelemetry, OutboxWriter outbox) {
        this.links = links;
        this.codes = codes;
        this.transactions = transactions;
        this.clock = clock;
        this.generator = generator;
        this.spans = new LinkSpans(openTelemetry);
        this.outbox = outbox;
    }

    /**
     * Issues a one-time code for the player; the player's previous open code for the provider is revoked.
     *
     * @throws net.onelitefeather.otis.problem.UnsupportedProviderProblem if the provider is unknown
     * @throws PlayerNotFoundProblem                                      if the player is not stored in Otis
     * @throws LinkCodeRateLimitedProblem                                 after 5 codes within 60 minutes
     */
    public LinkCodeDTO issueCode(UUID playerUuid, String providerName) {
        return spans.inSpan("links.code.create", playerUuid, observation -> {
            Provider provider = parseProvider(observation, providerName);
            UUID playerId = resolvePlayer(playerUuid);
            LinkCodeDTO issued = transactions.execute(() -> {
                Instant now = now();
                if (codes.countIssuedAfter(playerId, now.minus(RATE_WINDOW)) >= RATE_LIMIT) {
                    throw new LinkCodeRateLimitedProblem();
                }
                codes.deleteCreatedBefore(playerId, now.minus(CODE_RETENTION));
                codes.revokeOpen(playerId, provider.wireName(), now);
                String code = generator.generate();
                Instant expiresAt = now.plus(CODE_TTL);
                codes.save(new LinkCode(playerId, provider.wireName(),
                        LinkCodes.hash(LinkCodes.normalize(code).orElseThrow()), now, expiresAt));
                return new LinkCodeDTO(code, provider.wireName(), expiresAt);
            });
            finish(observation, "code.create", LinkSpans.ISSUED);
            return issued;
        });
    }

    /**
     * Redeems a code: consumes it and links the player of the code to the external account. Atomic: on any
     * failure the code stays unconsumed.
     *
     * @throws LinkCodeInvalidProblem               for an unknown, expired, used or foreign-provider code
     * @throws ExternalAccountAlreadyLinkedProblem  if another player owns the external account
     * @throws ProviderAlreadyLinkedProblem         if the player already has a verified link for the provider
     */
    public LinkLookupDTO redeem(RedeemRequestDTO request) {
        return spans.inSpan("links.redeem", null, observation -> {
            Provider provider = parseProvider(observation, request.provider());
            String hash = LinkCodes.normalize(request.code()).map(LinkCodes::hash)
                    .orElseThrow(LinkCodeInvalidProblem::new);
            Redeemed redeemed;
            try {
                redeemed = transactions.execute(() -> redeemInTransaction(observation, provider, hash, request));
            } catch (RuntimeException failure) {
                if (!isUniqueViolation(failure)) {
                    throw failure;
                }
                // a concurrent redeem won the race; the transaction rolled back, so the code is unconsumed
                LOGGER.atDebug().addKeyValue("operation", "redeem").addKeyValue("provider", provider.wireName())
                        .log("concurrent link detected");
                throw conflictFor(provider, request.externalId());
            }
            finish(observation, "redeem", redeemed.upgraded() ? LinkSpans.UPGRADED : LinkSpans.LINKED);
            return redeemed.result();
        });
    }

    /**
     * @return the player's links sorted by provider
     * @throws PlayerNotFoundProblem if the player is not stored in Otis
     */
    public List<AccountLinkDTO> list(UUID playerUuid) {
        return spans.inSpan("links.list", playerUuid, observation -> {
            UUID playerId = resolvePlayer(playerUuid);
            List<AccountLinkDTO> found = links.findAllByPlayer(playerId).stream().map(AccountLinkDTO::of).toList();
            finish(observation, "list", LinkSpans.FOUND);
            return found;
        });
    }

    /**
     * Sets or replaces the player's unverified link for the provider.
     *
     * @throws net.onelitefeather.otis.problem.InvalidLinkValueProblem if the value is not valid for the provider
     * @throws ProviderAlreadyLinkedProblem                            if the player has a verified link
     */
    public AccountLinkDTO putUnverified(UUID playerUuid, String providerName, String value) {
        return spans.inSpan("links.put", playerUuid, observation -> {
            Provider provider = parseProvider(observation, providerName);
            String valid = provider.validate(value);
            UUID playerId = resolvePlayer(playerUuid);
            AccountLink stored;
            try {
                stored = transactions.execute(() -> storeUnverified(playerId, provider, valid, playerUuid));
            } catch (RuntimeException failure) {
                if (!isUniqueViolation(failure)) {
                    throw failure;
                }
                // a concurrent first write won the race; the row exists now, so this attempt replaces or conflicts
                LOGGER.atDebug().addKeyValue("operation", "put").addKeyValue("provider", provider.wireName())
                        .log("concurrent first write detected, retrying once");
                stored = transactions.execute(() -> storeUnverified(playerId, provider, valid, playerUuid));
            }
            finish(observation, "put", LinkSpans.SET);
            return AccountLinkDTO.of(stored);
        });
    }

    /**
     * Removes the player's link for the provider. Removing a link that does not exist is not an error.
     *
     * @throws PlayerNotFoundProblem if the player is not stored in Otis
     */
    public void delete(UUID playerUuid, String providerName) {
        spans.inSpan("links.delete", playerUuid, observation -> {
            Provider provider = parseProvider(observation, providerName);
            UUID playerId = resolvePlayer(playerUuid);
            boolean deleted = transactions.execute(() -> deleteInTransaction(playerUuid, playerId, provider));
            finish(observation, "delete", deleted ? LinkSpans.DELETED : LinkSpans.NOT_FOUND);
            return null;
        });
    }

    /**
     * @return the owner and the verified link of the external account
     * @throws LinkNotFoundProblem if no verified link exists; unverified links are never found
     */
    public LinkLookupDTO lookup(String providerName, String externalId) {
        return spans.inSpan("links.lookup", null, observation -> {
            Provider provider = parseProvider(observation, providerName);
            AccountLink link = links.findVerified(provider.wireName(), externalId)
                    .orElseThrow(LinkNotFoundProblem::new);
            UUID playerUuid = links.findMojangUuid(link.getPlayerId()).orElseThrow(LinkNotFoundProblem::new);
            observation.player(playerUuid);
            finish(observation, "lookup", LinkSpans.FOUND);
            return new LinkLookupDTO(playerUuid, AccountLinkDTO.of(link));
        });
    }

    private record Redeemed(LinkLookupDTO result, boolean upgraded) {
    }

    /** Claim, conflict checks and write: any exception rolls all of it back. */
    private Redeemed redeemInTransaction(LinkSpans.Observation observation, Provider provider, String hash,
                                         RedeemRequestDTO request) {
        Instant now = now();
        if (codes.claim(hash, provider.wireName(), now) == 0) {
            throw new LinkCodeInvalidProblem();
        }
        UUID playerId = codes.findByCodeHash(hash).map(LinkCode::getPlayerId).orElseThrow(LinkCodeInvalidProblem::new);
        UUID playerUuid = links.findMojangUuid(playerId).orElseThrow(LinkCodeInvalidProblem::new);
        observation.player(playerUuid);

        Optional<AccountLink> owner = links.findVerified(provider.wireName(), request.externalId());
        if (owner.isPresent() && !owner.get().getPlayerId().equals(playerId)) {
            throw new ExternalAccountAlreadyLinkedProblem();
        }
        Optional<AccountLink> own = links.findByPlayerIdAndProvider(playerId, provider.wireName());
        if (own.isPresent() && own.get().isVerified()) {
            throw new ProviderAlreadyLinkedProblem();
        }
        AccountLink link;
        boolean upgraded = own.isPresent();
        if (upgraded) {
            link = own.get();
            link.setExternalId(request.externalId());
            link.setDisplayName(request.displayName());
            link.setLinkValue(null);
            link.setVerified(true);
            link.setLinkedAt(now);
            link = links.update(link);
        } else {
            link = links.save(new AccountLink(playerId, provider.wireName(), request.externalId(), null,
                    request.displayName(), true, now));
        }
        outbox.linked(playerUuid, provider.wireName(), request.externalId(), true, now);
        return new Redeemed(new LinkLookupDTO(playerUuid, AccountLinkDTO.of(link)), upgraded);
    }

    /** Removes the link and records the event in one transaction; a missing link changes nothing. */
    private boolean deleteInTransaction(UUID playerUuid, UUID playerId, Provider provider) {
        Optional<AccountLink> existing = links.findByPlayerIdAndProvider(playerId, provider.wireName());
        if (existing.isEmpty() || links.deleteByPlayerIdAndProvider(playerId, provider.wireName()) == 0) {
            return false;
        }
        AccountLink removed = existing.get();
        outbox.unlinked(playerUuid, provider.wireName(), removed.getExternalId(), removed.isVerified(), now());
        return true;
    }

    private AccountLink storeUnverified(UUID playerId, Provider provider, String value, UUID playerUuid) {
        Optional<AccountLink> existing = links.findByPlayerIdAndProvider(playerId, provider.wireName());
        Instant now = now();
        AccountLink stored;
        if (existing.isEmpty()) {
            stored = links.save(new AccountLink(playerId, provider.wireName(), null, value, null, false, now));
        } else {
            AccountLink link = existing.get();
            if (link.isVerified()) {
                throw new ProviderAlreadyLinkedProblem();
            }
            link.setLinkValue(value);
            link.setLinkedAt(now);
            stored = links.update(link);
        }
        outbox.linked(playerUuid, provider.wireName(), null, false, now);
        return stored;
    }

    /** After a lost race: the external account is taken if a verified link for it exists, else the provider is. */
    private RuntimeException conflictFor(Provider provider, String externalId) {
        return links.findVerified(provider.wireName(), externalId).isPresent()
                ? new ExternalAccountAlreadyLinkedProblem()
                : new ProviderAlreadyLinkedProblem();
    }

    private Provider parseProvider(LinkSpans.Observation observation, String providerName) {
        Provider provider = Provider.parse(providerName);
        observation.provider(provider);
        return provider;
    }

    private UUID resolvePlayer(UUID playerUuid) {
        return links.findPlayerIds(playerUuid).stream().findFirst().orElseThrow(PlayerNotFoundProblem::new);
    }

    private void finish(LinkSpans.Observation observation, String operation, String outcome) {
        observation.outcome(outcome);
        LOGGER.atDebug().addKeyValue("operation", operation).addKeyValue("provider", observation.provider())
                .addKeyValue("outcome", outcome).log("link operation finished");
    }

    /** Microsecond precision, so the value returned by a write equals the value read back from the database. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
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
