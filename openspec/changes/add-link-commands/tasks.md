# Tasks

## Execution Plan

Branch `feat/link-commands` from `origin/feat/account-links`; the PR base is `feat/account-links`. It runs in parallel to `add-account-link-events`.

Every agent prompt restates these rules:
- **Built-in first:** Velocity `BrigadierCommand`, Adventure `GlobalTranslator` and MiniMessage translation store, JDK virtual threads.
- **Java 25:** sealed `GatewayResult`, pattern `switch`, records.
- **i18n:** every player-facing text is a `Component.translatable` with keys `otis.link.*` in the `en` (fallback) and `de` bundles.
- **SLF4J:** WARN once per Otis failure, never player names, uuids or codes.
- **OpenTelemetry:** no spans in the plugin (the outgoing HTTP is a plain client call and the backend traces it).
- **Tests:** F.I.R.S.T. with a direct executor and no sleeps; test-first.
- **Commits:** Conventional Commits with the `Claude-Session` trailer. **Merge nothing.**

| Wave | Agent | Task IDs | Model | May Touch | Must Not Touch |
| ---- | ----- | -------- | ----- | --------- | -------------- |
| 1 | velocity-links | 1.1-1.2, 2.1-2.2, 3.1-3.3, 4.1-4.2 | sonnet | `velocity-plugin/**`, `openspec/changes/add-link-commands/**` | `backend/**`, `java-client/src/main/**`, `java-client/specs/**` |
| 2 | main session | 5.1-5.2 | - | PR metadata | source code |

## 1. Setup

- [x] 1.1 Copy `openspec/changes/add-link-commands/` from `/mnt/projects/oss/onelitefeather/Otis` into the worktree and commit it as `docs(openspec): add add-link-commands change`. Verify `openspec validate add-link-commands --strict` passes.
- [x] 1.2 Add `velocity-api` as `testImplementation`, plus JUnit and Logback test dependencies, to `velocity-plugin`. Check the Adventure version that `velocity-api` 4.2.0 brings and its translation store API (D4), and record the result in the PR notes. Verify `./gradlew :velocity-plugin:test` runs (no tests yet).

## 2. Translations

- [x] 2.1 (unit, red first) Write the bundle consistency test:
  - every key in `otis_en` is in `otis_de` and vice versa
  - every constant in `Messages` is in the fallback bundle

  Add `Messages` and both bundles (UTF-8, MiniMessage tags) with the keys from D4. Verify the test passes.
- [x] 2.2 (unit, red first) Rendering test: `OtisTranslations` is registered with `GlobalTranslator`. For `otis.link.list.empty`, `Locale.GERMAN` renders German and `Locale.FRENCH` renders the English fallback. Implement and verify.

## 3. Command logic

- [x] 3.1 (unit, red first) `LinkCommandHandler` tests with a fake `LinkGateway`, a capturing `Audience` and a direct executor, one per spec scenario:
  - code component with copy-to-clipboard and expiry
  - `github` rejected without a gateway call
  - list with verified/unverified markers, and empty list
  - unlink confirmation
  - social set
  - invalid value, provider already linked, rate limited
  - unavailable, with one WARN via a captured appender

  Implement `LinkCommandHandler` and the sealed `GatewayResult`, and verify.
- [ ] 3.2 (unit, red first) `OtisLinkGateway` tests with a stub `LinksApi`:
  - success mapping
  - `ApiException` with a problem body mapped to `Problem(slug)` via `ProblemDetails.from`
  - connection error and 5xx mapped to `Unavailable`

  Implement it with `OtisClients.newApiClient()` and the configured base URI, and verify.
- [ ] 3.3 (unit, red first) Brigadier tree tests:
  - `/link discord` dispatches to the handler with `discord`
  - the permission predicates deny sources without `otis.command.link|unlink|links|social`
  - console execution yields the players-only message

  Implement `LinkCommands` and the registration plus executor lifecycle in `OtisPlugin` (create on initialize, close on `ProxyShutdownEvent`). Verify the tests pass.

## 4. Build verification

- [ ] 4.1 Verify that `PlayerListener` is unchanged (`git diff` shows no change to it) and that the plugin still registers it.
- [ ] 4.2 Run `./gradlew clean build :velocity-plugin:shadowJar`. Verify that it is green and that the shadow JAR has no `net/kyori/` entries. Tick the boxes and push the branch.

## 5. Pull Request

- [ ] 5.1 Main session reviews the diff and tests against `specs/link-commands/spec.md`. Verify that every scenario maps to a passing test and that all player-facing strings are translation keys.
- [ ] 5.2 Open the pull request from `feat/link-commands` against base `feat/account-links`, titled `feat(velocity): add link commands to the proxy plugin`. The body includes the commands and permissions, the translation keys, the note that proxies need a restart (independent of the backend), and a final line `https://claude.ai/code/session_012BmpdMdQ5wwagagQvfcq8S`. **Do not merge.** Verify that `gh pr view` shows the PR as open.
