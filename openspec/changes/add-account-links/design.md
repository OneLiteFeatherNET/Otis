# Design

## Context

See proposal.md - Why. Otis (Micronaut 4.10, Java 25) already has a layered settings feature this design mirrors:
- `PlayerSettingController` under `/v1`
- `PlayerSettingService`, which owns its INTERNAL spans through the `SettingSpans` helper and gets the time from an injected `Clock`
- Micronaut Data repositories
- problems as subclasses of `OtisProblemException` with stable type URIs
- Flyway migrations per vendor (`db/migration/{h2,postgresql,mariadb}`), selected by `VendorMigrationLocations`
- integration tests on in-memory H2 with `@MicronautTest(transactional = false)`

Constraints that shape this design:
- **Transactions:** the classpath has both `micronaut-data-spring-jpa` and `micronaut-data-tx-hibernate`, which register two `@Primary` transaction operations beans. A plain declarative `@Transactional` is therefore ambiguous (`NonUniqueBeanException`), and the settings service deliberately works without transactions.
- **Players:** they are addressed by their Mojang uuid (`otis_player.player_uuid`, indexed but not unique) and referenced by the PK `otis_player.uuid`.
- **No service authentication:** Otis is exposed only on the internal gateway hostname.
- **Client spec:** it is hand-maintained (`java-client/specs/otis-api-1.1.0.yml`), and `OtisClients` configures the generated `ApiClient`.

## Goals / Non-Goals

**Goals:**
- Single-use, short-lived codes whose redeem is atomic. A failed redeem never consumes the code.
- External ids never leave the database except in API responses: not in logs, not in spans.
- Additive schema change, with existing endpoints and the client API untouched.

**Non-Goals:**
- Service-to-service authentication.
- OAuth flows with providers. The redeeming service authenticates the external user itself, for example a Discord interaction.
- Storing provider access tokens.
- Events (`add-account-link-events`) and in-game commands (`add-link-commands`).
- Discord Linked Roles.

## Decisions

### D1 - Data model: `account_link` and `link_code` (Flyway V3)

**`account_link`:**

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `player_id` | UUID | FK -> `otis_player(uuid)`, `ON DELETE CASCADE` |
| `provider` | varchar(16) | |
| `external_id` | varchar(64) | nullable; set for verified links |
| `link_value` | varchar(200) | nullable; handle or URL of unverified links |
| `display_name` | varchar(100) | nullable |
| `verified` | boolean | |
| `linked_at` | timestamp with time zone | |

- `UNIQUE(player_id, provider)`
- `UNIQUE(provider, external_id)`. NULLs are distinct on PostgreSQL, MariaDB and H2, so any number of unverified rows with `external_id IS NULL` coexist.

**`link_code`:**

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `player_id` | UUID | FK with cascade |
| `provider` | varchar(16) | |
| `code_hash` | char(64) | unique |
| `created_at` | timestamp with time zone | |
| `expires_at` | timestamp with time zone | |
| `used_at` | timestamp with time zone | nullable |
| `revoked_at` | timestamp with time zone | nullable |

- Index `(player_id, created_at)` for the rate limit.

**Rationale:**
- Same FK target and cascade as `player_setting`. The existing delete endpoint removes links without code changes.
- One table for verified and unverified links keeps "one link per provider" a single constraint.
- `link_value` and `external_id` are separate columns. Lookup only ever matches verified `external_id` values, so an unverified claim can never shadow a real account.
- Alternative: two tables (verified/unverified). Rejected, because the "at most one per provider" rule would span two tables.
- Vendor scripts follow V2 (`uuid` type, `timestamp(6) with time zone`; `boolean` on all three vendors).

**Tests:**
- Fresh-database migration test plus existing-database test (pre-V3 schema with rows stays identical).
- Hibernate `validate` in the test profile.

**SOLID:** SRP. One table holds one concept.

### D2 - Codes: JDK `SecureRandom`, Crockford Base32, SHA-256

**Built-in first:**
- A `SecureRandom` draws 40 bits, which are encoded as 8 Crockford Base32 characters. The alphabet is a constant of 32 characters, so no dependency is needed.
- `MessageDigest` SHA-256 runs over the normalized code (uppercase, `-` removed) to produce `code_hash`.

**Why it is safe:**
- No pepper and no redeem-attempt table. The code space is about 1.1e12, and with a 10-minute TTL online guessing is impractical, even more so since every failure looks the same (no oracle).
- An offline attack on a leaked hash is bounded by the TTL.

**Rejected alternatives:**
- UUIDs: too long to type in chat.
- 6 digits: about 1e6 combinations, guessable.
- bcrypt: needs a dependency, and slowness buys nothing for a 10-minute secret.

**Code structure:**
- `LinkCodes` is a small final class with `generate()`, `normalize(String)` and `hash(String)`.
- It takes the random source as a constructor parameter. The test passes a seeded `Random`, production passes `SecureRandom`.

**Tests:** unit tests for the alphabet and format, normalization (lowercase, missing dash), stable hashing, and a seeded generation.

**SOLID:** SRP; DIP (the random source is injected).

### D3 - Transactions: programmatic, explicitly qualified transaction operations

**What needs a transaction:** redeem must, atomically,
1. claim the code with a conditional update (`used_at IS NULL AND revoked_at IS NULL AND expires_at > now AND provider = ?`, affected rows = 1),
2. check the conflicts (D5),
3. insert or upgrade the link.

If anything fails, all of it rolls back, so the code stays unconsumed on 409. Issuing a code (revoking open codes, then inserting) also needs a transaction.

**Approach:**
- Introduce one `LinkTransactions` helper.
- It wraps the transaction operations of the Hibernate datasource, injected *explicitly by type*: `io.micronaut.transaction.TransactionOperations<org.hibernate.Session>` with the `default` datasource qualifier. This avoids the ambiguous `@Primary` pair.
- The first task is a red test that proves rollback, i.e. an exception after the claim leaves the code unconsumed. If the qualifier does not resolve uniquely, the test fails, and the fix is to name the Hibernate bean explicitly.

**Built-in options considered:**
- Declarative `@Transactional`: ambiguous today, see Context.
- Removing `micronaut-data-spring-jpa`: it would fix the ambiguity globally, but it is a cross-cutting refactor of shared code and belongs in its own `refactor` change.

`add-account-link-events` reuses `LinkTransactions` for the outbox write.

**Tests:** an integration test on H2 for rollback on failure; the concurrency test in D5.

**SOLID:** DIP. The service depends on a narrow transaction abstraction.

### D4 - Layers and API

- **Layers:**
  - `AccountLinkController`: HTTP plus OpenAPI annotations only.
  - `AccountLinkService`: rules, transactions and spans.
  - Repositories: `AccountLinkRepository`, `LinkCodeRepository`.
  - DTOs as records: `LinkCodeDTO`, `RedeemRequestDTO`, `AccountLinkDTO`, `LinkLookupDTO`, `ProfileLinkRequestDTO`.
  - `Provider`: an enum with lowercase JSON names and per-provider rules for unverified values (handle regex, allowed https hosts).
- **Endpoints:** as in the spec.
- **Provider in the path:** parsed in the service, not by a `TypeConverter`. This mirrors the settings decision that keeps specific problem types instead of a generic 400.
- **Unverified value rules,** one handle pattern and host list per enum constant:
  - discord: handles only
  - twitch: `twitch.tv`
  - youtube: `youtube.com`, `youtu.be`
  - x: `x.com`, `twitter.com`
  - tiktok: `tiktok.com`
  - github: `github.com`
- **URL check:** URLs must be `https`, with a host equal to an allowed host or a subdomain of it. They are parsed with `java.net.URI` (built-in).
- **Problems** (subclasses of `OtisProblemException`):
  - `unsupported-provider` (400)
  - `invalid-link-value` (400)
  - `link-code-rate-limited` (429)
  - `link-code-invalid` (410)
  - `external-account-already-linked` (409)
  - `provider-already-linked` (409)
  - `link-not-found` (404)
  - reused: `player-not-found` (404)

**Tests:**
- Unit tests for the `Provider` rules.
- REST Assured integration tests for every spec scenario.

**SOLID:** OCP. A new provider is a new enum constant, and controller and service stay unchanged.

### D5 - Conflict and race handling

**Redeem order** inside the D3 transaction:
1. Claim the code; if 0 rows were affected, answer 410.
2. Load the code's player.
3. If another player has a verified link for (provider, externalId), answer 409 `external-account-already-linked`.
4. If this player has a verified link for the provider, answer 409 `provider-already-linked`.
5. Otherwise upsert this player's row for the provider. That either upgrades an unverified row or inserts a new one.

A concurrent redeem can still violate `UNIQUE(provider, external_id)` or `UNIQUE(player_id, provider)` at commit. The unique violation is mapped to the same 409 types, and the transaction rolls back, so the code stays unconsumed.

**Tests:** an integration test with two virtual-thread redeems for the same external account but different players, without sleeps. Exactly one link is created, the other request gets 409, and its code is still redeemable.

### D6 - Rate limit from existing rows

- Before issuing, count the player's codes with `created_at > now - 60 min`. At 5 or more, answer 429.
- Codes older than 24h are deleted when the same player issues a code (opportunistic cleanup, no scheduler).
- Built-in option considered: the Caffeine cache, which is already a dependency. Rejected, because it is per replica (prod runs 2), so the limit would be 10 instead of 5.

**Tests:** service unit test with a fixed `Clock`: the 6th code is rejected, and a code is issued again after the window has passed.

### D7 - Observability

**Spans:**
- `LinkSpans` mirrors `SettingSpans`: INTERNAL spans from an injected `OpenTelemetry`, instrumentation scope `net.onelitefeather.otis`.
- Attributes: `otis.player.uuid` (when known), `otis.link.provider`, `otis.link.outcome`.
- Outcomes: `issued`, `rate_limited`, `linked`, `upgraded`, `invalid`, `conflict`, `set`, `deleted`, `found`, `not_found`, `rejected`.
- `SettingSpans` is not generalized. That would modify shared code another feature depends on, and the duplication is about 60 lines of glue.

**Logs:** DEBUG per operation with provider and outcome only. Codes, external ids, display names and values are never logged.

**Metrics:** none.
- The volume is a few links per day, so a counter answers no operational question that spans and the upcoming events don't.
- Failures are already covered by the existing HTTP 5xx alert.

**Tests:** `OpenTelemetryExtension` unit tests assert span names, kind, attributes and status per outcome, and that no attribute value equals the code, externalId or displayName.

### D8 - Client

- Extend `otis-api-1.1.0.yml` with the link paths and schemas, copied from the backend-generated OpenAPI.
- Generated `LinksApi`.
- The existing `OtisClients` needs no change, because there are no new custom types.

**Tests:**
- `javap -public` comparison: additions only.
- `velocity-plugin` compiles unchanged.
- A unit test that `OtisClients.newApiClient()` can deserialize an `AccountLinkDTO` JSON sample.

## Risks / Trade-offs

- **[No service auth: any internal caller can redeem with an arbitrary externalId]** → Otis stays internal-only (internal gateway hostname). This is documented in the PR, with a follow-up change for service authentication. The code still proves the player side.
- **[A leaked DB exposes active code hashes]** → They are only useful for 10 minutes, and codes are single use.
- **[Rate limit counts per player, not per IP or caller]** → This is sufficient, because codes are only issued for a player authenticated by the proxy.
- **[Concurrent redeems]** → Unique constraints plus a transaction and rollback (D5), covered by a test.
- **[`micronaut-data-spring-jpa` ambiguity hides other transactional needs]** → `LinkTransactions` is explicit. A separate refactor may remove the Spring module later.

## Migration Plan

1. Deploy a release containing V3. Flyway applies `CREATE TABLE account_link` and `CREATE TABLE link_code` on PostgreSQL. No existing table is altered.
2. Rollback: deploy the previous image. It ignores the new tables. Optional cleanup is `DROP TABLE link_code; DROP TABLE account_link;`; player and settings data are untouched.
