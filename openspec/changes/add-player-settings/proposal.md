# Proposal

Ships as: `feat(settings): add versioned player settings api with adventure keys`

## Why

Games and services on the network need to persist per-player settings. Some settings are generic and shared by every service (e.g. language), others belong to a single game (e.g. the lobby's player hider); the lobby, for example, needs both. Otis already is the central player data store, so it is the natural place for these settings, provided it stays agnostic of what each game stores.

## What Changes

- New per-player settings store: a setting is identified by the player's Mojang `playerUuid` plus an Adventure `Key` (`namespace:value`); its value is arbitrary JSON that Otis stores without interpreting it.
- Generic vs. game-specific settings are separated by the key namespace: `olf` is reserved for generic settings, each game uses its own namespace (e.g. `lobby`). A service reads exactly the namespaces it needs.
- Keys without an explicit namespace and keys in the `minecraft` namespace are rejected.
- One key per setting (fine-grained), so updates affect a single setting only.
- New versioned REST API under `/v1/players/{playerUuid}/settings`: list (filterable by namespace), get, put and delete single settings. Existing `/otis` and `/search` endpoints stay unchanged.
- Writes are last-write-wins. Responses carry a per-setting `version` and `updatedAt`, so conditional writes (`If-Match`) can be added later without breaking the API.
- Writes are idempotent: putting a value equal to the stored one (semantic JSON equality) is a no-op that leaves `version` and `updatedAt` untouched; deleting a missing setting succeeds; the first write creates the setting (upsert) and stays correct under concurrent first writes.
- Errors are reported as Problem Details (provided by `add-problem-details`), with setting-specific problem types such as invalid key, reserved or missing namespace, invalid value, player not found and setting not found.
- `net.kyori.adventure.key.Key` is used as the key type in the backend and in the public API of `java-client`; on the wire it is the `namespace:value` string.
- Settings get their own layers: controller, a new service layer, DTOs, repository and entity.
- Traces: every settings operation produces a span with the player uuid, key namespace and key as attributes, plus the outcome (created, updated, unchanged, deleted, not found). Setting values never appear in spans or logs.
- Spec: the backend's OpenAPI annotations fully describe the `/v1` settings endpoints (operations, request/response schemas, key format, problem responses), and the hand-maintained client spec is updated to match.

## Capabilities

### New Capabilities
- `player-settings`: storing, reading, listing and deleting per-player settings identified by Adventure keys, including namespace rules, idempotency and last-write-wins semantics, and the versioned `/v1` HTTP API.

### Modified Capabilities

## Impact

- **Backend**: new entity/table for player settings (unique per player, namespace and key value), repository, service (introduces the `service` layer to the codebase), controller and DTOs; Serde support for Adventure `Key`.
- **New dependencies**: `net.kyori:adventure-key` (backend `implementation`, java-client `compileOnly`; version aligned with the Adventure version shipped by `velocity-api` 4.2.0), added to the `libs` version catalog.
- **Observability**: spans via the existing OpenTelemetry setup (manual spans or `@WithSpan`-style annotations on the service); no new exporter configuration.
- **User-facing text**: only English, developer-facing problem texts; no player-facing text and no translation keys are added. Setting values may contain player-facing data defined by games, which Otis stores opaquely.
- **Database**: new table. Local MariaDB is updated via `hbm2ddl=update`; production PostgreSQL runs with `hbm2ddl=none`, so the table needs a migration in the Kubernetes-FLUX deployment before release.
- **API**: new `/v1` endpoints; introduces URI-based API versioning and a resource-oriented style next to the existing unversioned endpoints.
- **java-client**: hand-maintained spec gains the settings endpoints and a custom key format mapped to `Key` via generator type mappings; a Jackson module (de)serializes `Key`; `adventure-key` is a `compileOnly` dependency, so consumers bring Adventure themselves.
- **velocity-plugin**: must not shade `adventure-key` (Velocity ships Adventure).
- **Depends on**: `add-problem-details` must land first.
- **Out of scope**: a schema registry for setting definitions and central defaults; non-player (server/global) settings; generating the client spec from the backend build (planned as a separate `build(java-client)` change).
