# Design

## Context

See proposal.md - Why. Builds on `add-problem-details` (problem exception base type, handler, client `ProblemDetail`). The backend has one entity `OtisPlayer` (table `otis_player`, PK `uuid` UUID generated, `player_uuid` indexed but not unique, `profile_texture` JSON via `MapStringObjectConverter`, `locale` with default `'en-US'`). Controllers call the repository directly; there is no service layer. Local dev uses MariaDB with `hbm2ddl.auto=update`; production uses PostgreSQL with `hbm2ddl.auto=none`, and there is no migration tool in the repo. CI builds on ubuntu, windows and macos without Docker. `micronaut-tracing-opentelemetry-http` and `-jdbc` already create HTTP server and JDBC spans.

Requirements: see `specs/player-settings/spec.md`.

## Goals / Non-Goals

**Goals:**
- Settings stored and served through own layers (controller, service, DTOs, repository, entity).
- Schema introduced by versioned, additive migrations that never touch existing player data.
- Adventure `Key` end to end in Java code; plain `namespace:value` string on the wire.
- Use-case spans for settings operations.

**Non-Goals:**
- Schema registry, central defaults, non-player settings.
- Conditional writes (`If-Match`) - the response already carries `version` for later addition.
- Moving existing `/otis` and `/search` endpoints to `/v1` or behind the new service layer.
- New metrics.

## Decisions

### D1 - Data model: `player_setting` referencing `otis_player.uuid`
Columns: `id` (UUID PK), `player_id` (UUID, FK -> `otis_player.uuid` `ON DELETE CASCADE`), `key_namespace` (varchar 255), `key_value` (varchar 255), `setting_value` (JSON: `jsonb` on PostgreSQL, `JSON` on MariaDB, `JSON`/`CLOB` on H2), `version` (bigint), `updated_at` (timestamp with time zone). Unique constraint `(player_id, key_namespace, key_value)`, index `(player_id, key_namespace)` serves the namespace filter.
- The FK targets the PK `otis_player.uuid` instead of `player_uuid` because `player_uuid` is not unique; making it unique would need an `ALTER` on the existing table that can fail on duplicates - violating "additive only". The API keeps addressing players by Mojang uuid; the service resolves it via `OtisPlayerRepository.findByPlayerUuid`. Cascade delete keeps the existing delete endpoint's behavior while removing orphaned settings, without changing its code.
- Namespace and value in separate columns so the namespace filter is an index lookup (alternative: single `key` column with `LIKE 'ns:%'` - rejected).
- Entity `PlayerSetting` exposes `Key getKey()` built from both columns (`Key.key(namespace, value)`); the JSON value is a `JsonNode`-like tree mapped with `@JdbcTypeCode(SqlTypes.JSON)` (built-in Hibernate JSON mapping, as already used by `OtisPlayer.profileTexture`).
- `version` is a plain counter managed by the service, not JPA `@Version` (JPA optimistic locking would turn concurrent last-write-wins updates into failures - the opposite of the agreed semantics).
- Testing: integration tests against H2 (repository + migration); unit tests for entity <-> DTO mapping.
- SOLID: Single Responsibility (entity holds state only).

### D2 - Flyway via `micronaut-flyway` with baseline
Add `io.micronaut.flyway:micronaut-flyway` plus `org.flywaydb:flyway-database-postgresql` and `org.flywaydb:flyway-mysql` (MariaDB support); versions from the Micronaut platform BOM. Config: `flyway.datasources.default.enabled: ${FLYWAY_ENABLED:true}`, `baseline-on-migrate: true`, `baseline-version: 1`, `locations: classpath:db/migration/{vendor}` (Flyway's `{vendor}` placeholder resolves to `postgresql`, `mariadb`/`mysql`, `h2`; the implementer verifies the exact vendor names and adds directories accordingly).
- `V1__otis_player.sql`: `CREATE TABLE IF NOT EXISTS otis_player (...)` matching the current Hibernate-generated schema (columns, types, the two indexes, `locale` default). Only runs on an empty database; an existing database without history is baselined at version 1, so V1 never runs there and no existing row is touched.
- `V2__player_setting.sql`: creates `player_setting` (D1). Additive only: no `ALTER`/`DROP` on existing objects.
- `hbm2ddl.auto` stays `update` locally and `none` in prod. Micronaut Flyway runs migrations when the datasource is created, before Hibernate's schema handling, so local `update` finds the tables already created.
- Built-in option: `micronaut-flyway` is Micronaut's built-in migration integration. Alternatives: Liquibase (also supported; rejected - XML/YAML changelogs heavier for two SQL files), hand-applied SQL in the Kubernetes-FLUX repo (rejected - manual, untested, drifts from code), `hbm2ddl=update` in prod (rejected - uncontrolled DDL against prod).
- Testing: integration test "existing database": start with H2 pre-populated by a SQL fixture of the current schema + rows and *no* `flyway_schema_history`, run Flyway, assert rows unchanged and `player_setting` exists; integration test "fresh database": empty H2, assert both tables. Both use a fresh in-memory DB name per test (no shared state).
- SOLID: n/a (infrastructure configuration).
- Logging: Flyway's own INFO lifecycle logs (applied migrations) - operator relevant, no own log lines.

### D3 - Layers and Adventure `Key` serialization
Packages follow the existing layer layout: `controller.PlayerSettingController` (`/v1/players/{playerUuid}/settings`), `service.PlayerSettingService` (new layer), `dto.PlayerSettingDTO` (record: `Key key`, `JsonNode value`, `long version`, `Instant updatedAt`), `database.entity.PlayerSetting`, `database.repository.PlayerSettingRepository` (Micronaut Data `CrudRepository`, derived queries `findByPlayerIdAndKeyNamespaceAndKeyValue`, `findByPlayerIdAndKeyNamespaceIn`, `findByPlayerId`). Key (de)serialization in the backend: a Micronaut Serde `Serde<Key>` bean (`Key.key(String)` + namespace rules on read, `asString()` on write); path parameter binding via a `TypeConverter<String, Key>`.
- Key parsing and namespace rules live in one `SettingKeys.parse(String)` that throws problem subtypes `MissingNamespaceProblem`, `ReservedNamespaceProblem`, `InvalidSettingKeyProblem` (subtypes of the base from `add-problem-details`). It checks the raw string for `:` before calling `Key.key`, because `Key.key("x")` silently defaults to `minecraft:x`.
- Built-in option: Adventure's own `Key.key` validation (`InvalidKeyException`) instead of an own regex; Micronaut Serde and `TypeConverter` instead of manual parsing in the controller.
- Dependency: `net.kyori:adventure-key`, version = the Adventure version bundled by `velocity-api` 4.2.0 (implementer reads it from `velocity-api`'s POM), added to the `libs` catalog in `settings.gradle.kts`.
- Testing: unit tests for `SettingKeys` (missing namespace, `minecraft`, invalid chars, valid `olf:` and `lobby:` keys) and for the Serde (round trip).
- SOLID: Single Responsibility (parsing in one place), Dependency Inversion (controller depends on service, service on repository interfaces).

### D4 - Service semantics: idempotent upsert with retry
`PlayerSettingService.put(playerUuid, key, value)`:
1. Validate size (serialized bytes <= 65536, else `SettingValueTooLargeProblem` 413).
2. Resolve player (`PlayerNotFoundProblem` 404).
3. In a transaction: find setting; if present and `JsonNode.equals` (semantic equality, key order irrelevant) -> return `UNCHANGED`; if present -> replace value, `version + 1`, `updatedAt = clock.instant()` -> `UPDATED`; if absent -> insert `version 1` -> `CREATED`.
4. On unique-constraint violation from a concurrent insert, retry the transaction once (it then sees the row and updates or no-ops).
`delete` is idempotent (`DELETED` or `NOT_FOUND`, both 204). The result is a sealed `PutResult` (`Created`/`Updated`/`Unchanged`) the controller maps with an exhaustive `switch` to 201/200.
- `Clock` is injected (bean `Clock.systemUTC()`), tests pass a fixed clock.
- Built-in option: Micronaut `@Transactional`; native `ON CONFLICT`/`ON DUPLICATE KEY` rejected (vendor-specific SQL, prod and local differ); `@Retryable` considered - a single explicit retry is clearer and only for the constraint violation.
- Testing: unit tests with an in-memory fake repository and fixed clock for create/update/unchanged/delete/not-found/too-large; integration test for concurrent first write (two `CompletableFuture`s on virtual threads against H2, joined - no sleeps) asserting one row and no 5xx.
- SOLID: Single Responsibility (service holds rules), Open/Closed via sealed results.
- Logging: `LOGGER.atDebug()` per operation with key-values `player`, `namespace`, `outcome`; never the value.

### D5 - Use-case spans via injected `Tracer`
The service receives `OpenTelemetry` (Micronaut provides the bean; tests inject the SDK from `OpenTelemetryExtension`) and creates `Tracer` scope `net.onelitefeather.otis`. Each operation starts one `SpanKind.INTERNAL` span `settings.list|get|put|delete`, made current with try-with-resources, ended in `finally`, attributes `otis.player.uuid`, `otis.setting.namespace`, `otis.setting.key`, `otis.setting.outcome`. Parent is the current HTTP server span (Micronaut context propagation). Expected outcomes do not set ERROR; unexpected exceptions are recorded once by the problem handler from `add-problem-details`, not here.
- Built-in option: Micronaut `@NewSpan`/`@SpanTag` considered - rejected because the outcome attribute is only known at the end and annotation-driven spans need a running context to test; HTTP and JDBC spans stay with Micronaut's integration.
- Testing: unit tests with `OpenTelemetryExtension` asserting name, kind, attributes, parent and that no attribute contains the value.
- SOLID: Dependency Inversion (API only, injected instance).
- Metrics: none - not requested and no operational question yet; HTTP server metrics already cover rates and status codes.

### D6 - API surface and problems
`GET /v1/players/{playerUuid}/settings?namespace=...` -> `200 [PlayerSettingDTO]`; `GET .../{key}` -> 200/404; `PUT .../{key}` (body: any JSON) -> 201/200; `DELETE .../{key}` -> 204. Request size is additionally capped by `micronaut.server.max-request-size` default; the 64 KiB check is in the service so it yields a problem. New problem slugs: `missing-namespace`, `reserved-namespace`, `invalid-setting-key`, `invalid-setting-value`, `setting-value-too-large`, `player-not-found`, `setting-not-found`. All endpoints carry `@Operation`/`@ApiResponse` including problem responses and `@Schema(type = "string", format = "adventure-key", example = "lobby:player_hider")` on key parameters/fields.
- Testing: REST Assured integration tests per spec scenario against H2.
- SOLID: Single Responsibility (controller only maps HTTP).

### D7 - Client: `Key` in the public API
`java-client/specs/otis-api-1.1.0.yml` (from `add-problem-details`) gains the settings paths and `PlayerSetting` schema with `format: adventure-key`. Generator config adds `typeMappings` `string+adventure-key -> Key` and `importMappings` `Key -> net.kyori.adventure.key.Key`. A Jackson `SimpleModule` (`AdventureKeyModule`) is registered on the generated `ObjectMapper` (`JSON.getDefault().getMapper().registerModule(...)` in a small `OtisClients` factory, or via generator template hook if available). `adventure-key` is `compileOnly` in `java-client`; `velocity-plugin` excludes it from the shadow JAR (Velocity provides Adventure). Path encoding of a `Key` uses `asString()`; verified by a unit test of the generated request URI.
- Built-in option: OpenAPI generator type/import mappings and Jackson modules; no custom templates unless mappings cannot express it.
- Testing: unit tests (module round trip, URI building with `:` in path); `javap -public` comparison of existing APIs/models before/after; `velocity-plugin` compiles unchanged; shadow JAR inspected to contain no `net/kyori/adventure/key` classes.
- SOLID: Open/Closed (additive client API).

## Risks / Trade-offs

- [Prod DB user lacks CREATE privilege for `flyway_schema_history`] -> `FLYWAY_ENABLED=false` lets the service start; then V2 is applied manually from the same SQL file. Documented in the migration plan.
- [V1 differs from the real prod schema] -> V1 never runs on prod (baseline); it only shapes fresh databases. Integration test compares V1 result with Hibernate's expectations (`hbm2ddl=validate` in the fresh-DB test).
- [Hibernate `update` locally creates `player_setting` differently than V2] -> Flyway runs first; tests run with `hbm2ddl=validate` to catch mapping/DDL mismatch.
- [JSON column type differences across vendors] -> vendor-specific migration directories; equality is evaluated in Java (`JsonNode.equals`), never in SQL.
- [Concurrent first write still fails after one retry under extreme contention] -> Acceptable; would surface as 5xx with `traceId`; integration test covers the two-writer case.
- [Clients without Adventure on the classpath] -> All realistic consumers (Velocity, Paper, Minestom) ship Adventure; documented in the client README/Javadoc.

## Migration Plan

1. Deploy release containing Flyway. On startup against prod: Flyway sees a non-empty schema without history, baselines at V1, applies V2 (`CREATE TABLE player_setting`). No existing table is altered.
2. If startup fails due to missing privileges: set `FLYWAY_ENABLED=false`, apply `db/migration/postgresql/V2__player_setting.sql` manually, then insert the baseline and V2 rows by re-enabling Flyway once privileges exist.
3. Rollback: deploy the previous image; it ignores `player_setting` and `flyway_schema_history`. Optional cleanup: `DROP TABLE player_setting` - existing player data is untouched in every step.
