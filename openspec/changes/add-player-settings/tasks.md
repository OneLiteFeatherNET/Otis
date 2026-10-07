# Tasks

## Execution Plan

Branch `feat/player-settings` from `origin/feat/problem-details` (stacked; PR base `feat/problem-details`). All agents are Sonnet in their own worktree on that branch. Wave 1 is one agent (schema, domain, service, API depend on each other); wave 2 is the client agent after wave 1 is pushed. Every agent prompt restates: built-in first, Java 25 features (records, sealed result types, exhaustive pattern `switch`, virtual threads in the concurrency test), no player-facing text (no i18n bundles), SLF4J rules (DEBUG per operation, never log values or personal data), OpenTelemetry API only in main code with injected `OpenTelemetry`, F.I.R.S.T. tests with injected `Clock`, test-first, Conventional Commits with the `Claude-Session` trailer.

| Wave | Agent | Task IDs | Model | May Touch | Must Not Touch |
| ---- | ----- | -------- | ----- | --------- | -------------- |
| 1 | backend-settings | 1.1-1.2, 2.1-2.3, 3.1-3.4, 4.1-4.3 | sonnet | `backend/**`, `settings.gradle.kts`, `openspec/changes/add-player-settings/**` | `java-client/**`, `velocity-plugin/**`, existing controllers' behavior |
| 2 | client-settings | 5.1-5.4, 6.1 | sonnet | `java-client/**`, `velocity-plugin/build.gradle.kts`, `openspec/changes/add-player-settings/tasks.md` | `backend/src/main/**` |
| 3 | main session | 7.1-7.2 | - | PR metadata | source code |

## 1. Setup

- [x] 1.1 Copy `openspec/changes/add-player-settings/` from `/mnt/projects/oss/onelitefeather/Otis` into the worktree and commit as `docs(openspec): add add-player-settings change`; verify `openspec validate add-player-settings --strict` passes
- [x] 1.2 Add `net.kyori:adventure-key` (version = Adventure version of `velocity-api` 4.2.0, read from its POM), `micronaut-flyway`, `flyway-database-postgresql`, `flyway-mysql` to the `libs` catalog / backend dependencies; verify `./gradlew :backend:dependencies` resolves them

## 2. Schema migration

- [x] 2.1 (integration, red first) Write a test "fresh database": empty in-memory H2 (unique DB name per test), Flyway migrates, Hibernate `validate` passes, both tables exist; write V1 (`otis_player` exactly as Hibernate generates it today) and V2 (`player_setting`, D1) for `h2`, `postgresql`, `mariadb` vendor directories; configure Flyway (`FLYWAY_ENABLED` toggle, baseline-on-migrate, baseline-version 1); verify the test passes
- [x] 2.2 (integration, red first) Write a test "existing database": H2 pre-filled by a fixture with the current `otis_player` schema and rows, no history table; after startup all rows are byte-identical and `player_setting` exists; verify it passes
- [ ] 2.3 Review V1/V2 SQL for PostgreSQL and MariaDB by hand against D1 (types `jsonb`/`JSON`, FK `ON DELETE CASCADE`, unique and index); verify by starting `./gradlew :backend:run` against the local MariaDB from `docker/docker-compose.yml` if Docker is available, otherwise record that the manual check was skipped in the PR body

## 3. Domain and service

- [x] 3.1 (unit, red first) Tests for `SettingKeys.parse`: missing namespace, `minecraft:`, invalid characters, valid `olf:` / `lobby:` keys -> corresponding problem subtypes; plus Serde/`TypeConverter` round trip for `Key`; implement; verify tests pass
- [x] 3.2 (unit, red first) Tests for `PlayerSettingService` with an in-memory fake repository and fixed `Clock`: created (version 1), updated (version n+1, new `updatedAt`), unchanged on semantically equal JSON (key order differs), delete existing/missing, player not found, value > 65536 bytes -> too large; implement entity, repository, DTO, sealed `PutResult`; verify tests pass
- [x] 3.3 (unit, red first) `OpenTelemetryExtension` tests: one INTERNAL span per operation with name, attributes (player uuid, namespace, key, outcome), parent = current span, not ERROR for not-found/rejected, no attribute contains the value; implement D5; verify tests pass
- [x] 3.4 (integration, red first) Concurrent first write: two virtual-thread tasks put the same new key against H2, joined without sleeps; assert both succeed (200/201) and exactly one row exists; implement the one-time retry on unique violation; verify the test passes

## 4. API

- [x] 4.1 (integration, red first) REST Assured tests for every scenario of "Versioned settings API", "Listing filters by namespace", "Key namespace rules", "Settings belong to known players", "Setting values are bounded" (status, problem `type`, body); implement `PlayerSettingController` with `@Operation`/`@ApiResponse` and `format: adventure-key` schemas; verify tests pass
- [x] 4.2 (integration) Test "Deleting a player is unaffected by settings": delete a player with settings via `POST /otis/delete/{owner}`, assert same response as before and settings gone; rerun the characterization tests from `add-problem-details`; verify all pass
- [ ] 4.3 Run `./gradlew :backend:build`; verify green and that the generated backend OpenAPI YAML contains the `/v1/players/{playerUuid}/settings` paths; push the branch

## 5. Client

- [ ] 5.1 Save `javap -public` baseline of generated `PlayerApi`, `SearchApi` and models from the branch state before client changes
- [ ] 5.2 Extend `java-client/specs/otis-api-1.1.0.yml` with the settings paths and schemas (copied from the backend-generated YAML), add `typeMappings`/`importMappings` for `adventure-key` -> `net.kyori.adventure.key.Key`, add `adventure-key` as `compileOnly`; verify the generated settings API uses `Key`
- [ ] 5.3 (unit, red first) Tests for `AdventureKeyModule` round trip and for the generated request URI of a `Key` path parameter (`lobby:player_hider`); implement and register the module on the generated mapper; verify tests pass
- [ ] 5.4 Compare `javap -public` with 5.1 (only additions); build `./gradlew :velocity-plugin:shadowJar` without source changes and verify the JAR contains no `net/kyori/adventure/key/` classes (exclude in shadow config if needed)

## 6. Full build

- [ ] 6.1 Run `./gradlew clean build`; verify green; tick all completed boxes in this file

## 7. Pull Request

- [ ] 7.1 Main session reviews the diff and tests against `specs/player-settings/spec.md`; verify every scenario maps to a passing test and the migration plan in `design.md` matches the shipped SQL
- [ ] 7.2 Push `feat/player-settings` and open the pull request against base `feat/problem-details` titled `feat(settings): add versioned player settings api with adventure keys`, body including the migration plan (`FLYWAY_ENABLED`, rollback) and ending with `https://claude.ai/code/session_012BmpdMdQ5wwagagQvfcq8S`; verify `gh pr view` shows the PR
