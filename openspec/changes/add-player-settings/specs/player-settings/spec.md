# Spec Delta

## Purpose

Lets games and services persist per-player settings in Otis, identified by namespaced keys, with generic settings shared across the network and game-specific settings kept apart by namespace.

## ADDED Requirements

### Requirement: Settings are identified by player and namespaced key
The system SHALL store each setting under the Mojang player uuid of a player known to Otis and a key of the form `namespace:value` following the Adventure key syntax (namespace characters `[a-z0-9_.-]`, value characters `[a-z0-9_./-]`). Each pair of player uuid and key SHALL hold at most one setting. The value SHALL be any JSON value (object, array, string, number, boolean or null) that the system stores and returns without interpreting it.

#### Scenario: Store and read a setting
- **WHEN** a client puts the JSON value `{"enabled":true}` for player `P` and key `lobby:player_hider`
- **AND** then gets player `P`'s setting `lobby:player_hider`
- **THEN** the returned value is `{"enabled":true}` (semantically equal JSON)

#### Scenario: Same key for different players
- **WHEN** players `P1` and `P2` each store a value under `olf:language`
- **THEN** each player's setting returns its own value

#### Scenario: Scalar JSON value
- **WHEN** a client puts the JSON value `"de_de"` under `olf:language`
- **THEN** reading the setting returns the string `"de_de"`

### Requirement: Key namespace rules
The system SHALL reject a key without an explicit namespace with status 400 and problem type `https://otis.onelitefeather.net/problems/missing-namespace`, a key in namespace `minecraft` with status 400 and problem type `https://otis.onelitefeather.net/problems/reserved-namespace`, and a key that is not valid Adventure key syntax with status 400 and problem type `https://otis.onelitefeather.net/problems/invalid-setting-key`. The namespace `olf` SHALL be accepted and is reserved by convention for generic settings shared by all services.

#### Scenario: Missing namespace
- **WHEN** a client puts a setting with key `player_hider`
- **THEN** the response status is 400 with problem type `missing-namespace`
- **AND** nothing is stored

#### Scenario: Minecraft namespace
- **WHEN** a client puts a setting with key `minecraft:player_hider`
- **THEN** the response status is 400 with problem type `reserved-namespace`

#### Scenario: Invalid characters
- **WHEN** a client puts a setting with key `Lobby:Player Hider`
- **THEN** the response status is 400 with problem type `invalid-setting-key`

#### Scenario: Generic namespace accepted
- **WHEN** a client puts a setting with key `olf:language`
- **THEN** the setting is stored

### Requirement: Settings belong to known players
The system SHALL answer any settings request for a player uuid that is not stored in Otis with status 404 and problem type `https://otis.onelitefeather.net/problems/player-not-found`, and SHALL NOT create a player implicitly.

#### Scenario: Put for unknown player
- **WHEN** a client puts a setting for a player uuid unknown to Otis
- **THEN** the response status is 404 with problem type `player-not-found`
- **AND** no setting and no player is stored

#### Scenario: List for unknown player
- **WHEN** a client lists settings for a player uuid unknown to Otis
- **THEN** the response status is 404 with problem type `player-not-found`

### Requirement: Versioned settings API
The system SHALL expose settings under the versioned path prefix `/v1/players/{playerUuid}/settings` with the operations: `GET` on the collection (list), `GET`, `PUT` and `DELETE` on `/{key}`, where `{key}` is the full `namespace:value` key. Responses for a single setting SHALL contain `key`, `value`, `version` (positive integer) and `updatedAt` (ISO-8601 instant).

#### Scenario: Create a setting
- **WHEN** a client sends `PUT /v1/players/{P}/settings/lobby:player_hider` with a JSON body for a setting that does not exist
- **THEN** the response status is 201
- **AND** the body contains `key` `lobby:player_hider`, the value, `version` 1 and `updatedAt`

#### Scenario: Replace a setting
- **WHEN** a client puts a different value for an existing setting with version `n`
- **THEN** the response status is 200
- **AND** the stored value is replaced and `version` is `n + 1`

#### Scenario: Get missing setting
- **WHEN** a client gets a key that player `P` has no setting for
- **THEN** the response status is 404 with problem type `https://otis.onelitefeather.net/problems/setting-not-found`

#### Scenario: Delete a setting
- **WHEN** a client deletes an existing setting
- **THEN** the response status is 204
- **AND** a subsequent get returns 404 `setting-not-found`

### Requirement: Listing filters by namespace
The system SHALL return all settings of a player when listing without filter, and only settings whose key namespace matches one of the given `namespace` query parameters when one or more are given. A player without settings SHALL yield an empty list with status 200.

#### Scenario: Read generic and game settings
- **WHEN** player `P` has settings `olf:language`, `lobby:player_hider` and `bedwars:shop_layout`
- **AND** a client lists with `?namespace=olf&namespace=lobby`
- **THEN** the result contains exactly `olf:language` and `lobby:player_hider`

#### Scenario: List without filter
- **WHEN** a client lists player `P`'s settings without `namespace` parameter
- **THEN** all of `P`'s settings are returned

#### Scenario: No settings
- **WHEN** a known player has no settings
- **THEN** listing returns status 200 with an empty list

### Requirement: Writes are last-write-wins and idempotent
The system SHALL apply writes in last-write-wins order without requiring a precondition. A put whose value is semantically equal JSON to the stored value SHALL change nothing (neither value, `version` nor `updatedAt`) and return status 200 with the current setting. Deleting a setting that does not exist SHALL return status 204. Concurrent first writes for the same player and key SHALL result in exactly one stored setting and SHALL NOT fail with a server error.

#### Scenario: Repeated identical put
- **WHEN** a client puts `{"a":1,"b":2}` for an existing setting whose stored value is `{"b":2,"a":1}`
- **THEN** the response status is 200
- **AND** `version` and `updatedAt` are unchanged

#### Scenario: Last write wins
- **WHEN** two clients put different values for the same setting one after another
- **THEN** the setting holds the value of the later put

#### Scenario: Delete missing setting
- **WHEN** a client deletes a key that player `P` has no setting for
- **THEN** the response status is 204

#### Scenario: Concurrent first write
- **WHEN** two requests create the same not yet existing setting at the same time
- **THEN** both requests succeed with status 200 or 201
- **AND** exactly one setting exists afterwards holding one of the two values

### Requirement: Setting values are bounded
The system SHALL reject a put whose body is not valid JSON with status 400 and problem type `https://otis.onelitefeather.net/problems/invalid-setting-value`, and a put whose serialized value exceeds 64 KiB with status 413 and problem type `https://otis.onelitefeather.net/problems/setting-value-too-large`.

#### Scenario: Invalid JSON body
- **WHEN** a client puts a body that is not valid JSON
- **THEN** the response status is 400 with problem type `invalid-setting-value`

#### Scenario: Value too large
- **WHEN** a client puts a value whose serialized form is larger than 65536 bytes
- **THEN** the response status is 413 with problem type `setting-value-too-large`
- **AND** the stored setting, if any, is unchanged

### Requirement: Settings operations are traced without values
The system SHALL record one internal span per settings operation (named `settings.list`, `settings.get`, `settings.put`, `settings.delete`) as a child of the request's server span, carrying the player uuid, the key namespace, the key (for single-setting operations) and the outcome (`created`, `updated`, `unchanged`, `deleted`, `found`, `not_found`, `rejected`). Setting values SHALL NOT appear in spans or logs. Expected outcomes (not found, rejected input) SHALL NOT set the span status to error.

#### Scenario: Put creates span
- **WHEN** tracing is enabled and a client creates setting `lobby:player_hider` for player `P`
- **THEN** a span `settings.put` exists as child of the server span with attributes for `P`, namespace `lobby`, key `lobby:player_hider` and outcome `created`
- **AND** no span attribute contains the setting value

#### Scenario: Not found is no span error
- **WHEN** a client gets a missing setting
- **THEN** the `settings.get` span has outcome `not_found` and its status is not ERROR

### Requirement: Existing data and endpoints are preserved
The system SHALL introduce settings storage through additive schema migrations only: upgrading an existing database SHALL keep every existing player row and column unchanged, and existing endpoints under `/otis` and `/search` SHALL keep their behavior. Clients built against the previous API (including released `velocity-plugin` builds) SHALL keep working without modification.

#### Scenario: Upgrade existing database
- **WHEN** the new version starts against a database that already contains players but no migration history
- **THEN** all existing player rows are still present with identical values
- **AND** the settings storage exists afterwards

#### Scenario: Fresh database
- **WHEN** the new version starts against an empty database
- **THEN** both the player storage and the settings storage are created

#### Scenario: Old client keeps working
- **WHEN** a client built against the previous API calls `GET /otis/byId/{owner}`
- **THEN** it receives the same status and body as before the change

#### Scenario: Deleting a player is unaffected by settings
- **WHEN** a client deletes a player that has settings via the existing delete endpoint
- **THEN** the player is deleted with the same response as before the change
- **AND** the player's settings are deleted with it
