# Tasks

## Execution Plan

Branch `feat/account-links` from `origin/main`. All agents are Sonnet, each in its own worktree on that branch.

Every agent prompt restates these rules:
- **Built-in first:** JDK `SecureRandom`, `MessageDigest` and `URI`; Micronaut Data; Flyway.
- **Java 25:** records, enums, sealed result types, pattern `switch`, `_`, virtual threads in the concurrency test.
- **i18n:** no player-facing text.
- **SLF4J:** DEBUG per operation; never log codes, external ids, display names or link values.
- **OpenTelemetry:** API only, injected `OpenTelemetry`.
- **Tests:** F.I.R.S.T. with an injected `Clock` and seeded randomness in tests; test-first.
- **Commits:** Conventional Commits with the `Claude-Session` trailer. The user said: **merge nothing**.

| Wave | Agent | Task IDs | Model | May Touch | Must Not Touch |
| ---- | ----- | -------- | ----- | --------- | -------------- |
| 1 | backend-links | 1.1, 2.1-2.2, 3.1-3.5, 4.1-4.3 | sonnet | `backend/**`, `openspec/changes/add-account-links/**` | `java-client/**`, `velocity-plugin/**`, existing controllers and settings code |
| 2 | client-links | 5.1-5.3, 6.1 | sonnet | `java-client/**`, `openspec/changes/add-account-links/tasks.md` | `backend/src/main/**`, `velocity-plugin/src/**` |
| 3 | main session | 7.1-7.2 | - | PR metadata | source code |

## 1. Setup

- [x] 1.1 Copy `openspec/changes/add-account-links/` from `/mnt/projects/oss/onelitefeather/Otis` into the worktree and commit it as `docs(openspec): add add-account-links change`. Verify `openspec validate add-account-links --strict` passes.

## 2. Schema

- [x] 2.1 (integration, red first) Extend the fresh-database migration test to expect `account_link` and `link_code`. Write V3 for `h2`, `postgresql` and `mariadb` (D1), plus the entities. Verify that Hibernate `validate` and the test pass.
- [x] 2.2 (integration, red first) Extend the existing-database migration test: the pre-V3 schema with players and settings rows is unchanged after the migration, and V3 is applied. Verify that it passes.

## 3. Domain and service

- [x] 3.1 (unit, red first) Tests for `LinkCodes`:
  - the format matches `^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$`
  - normalization accepts lowercase input and a missing dash
  - the hash is stable and 64 hex characters
  - generation with a seeded `Random` is deterministic

  Implement it (D2) and verify the tests pass.
- [x] 3.2 (unit, red first) Tests for `Provider`:
  - lowercase wire names
  - unsupported provider -> problem
  - valid and invalid handles and URLs per provider, including a foreign host, `http`, and more than 200 characters

  Implement it (D4) and verify the tests pass.
- [x] 3.3 (integration, red first) Rollback test for `LinkTransactions`: an exception after the code claim leaves the code unconsumed on H2. Implement D3 with the explicitly qualified transaction operations. Verify it passes, and record which bean resolved in the PR notes.
- [ ] 3.4 (unit, red first) `AccountLinkService` tests with fake repositories and a fixed `Clock` for every rule:
  - issue a code; the previous code is revoked
  - rate limit at 6 codes, and again after the window
  - redeem: success, lenient input, used, expired at +10:01, provider mismatch
  - conflicts: `external-account-already-linked` and `provider-already-linked`, with the code still open afterwards
  - re-link after unlink
  - unverified links: set, replace, rejected over a verified link, upgraded by redeem
  - list, idempotent delete, and lookup ignoring unverified links

  Implement and verify.
- [ ] 3.5 (unit, red first) `OpenTelemetryExtension` tests (D7):
  - one INTERNAL span per operation, with name, provider, outcome and player uuid
  - expected outcomes are not ERROR
  - no attribute equals the code, externalId, displayName or value

  Implement `LinkSpans` and verify.

## 4. API

- [ ] 4.1 (integration, red first) REST Assured tests for every scenario in `specs/account-links/spec.md` (status, problem `type`, body). Implement `AccountLinkController` with `@Operation` and `@ApiResponse` including problem responses. Verify the tests pass.
- [ ] 4.2 (integration, red first)
  - Concurrency test (D5): two virtual-thread redeems for the same externalId from different players, joined without sleeps. Assert one 201 and one 409, and that the losing code is still redeemable.
  - Cascade test: player delete via `POST /otis/delete/{owner}` removes the links.
  - Rerun `ExistingEndpointsCompatibilityTest` and the settings tests.

  Verify all pass.
- [ ] 4.3 Run `./gradlew :backend:build`. Verify it is green and that the generated backend OpenAPI YAML contains the link paths. Push the branch.

## 5. Client

- [ ] 5.1 Save a `javap -public` baseline of all generated client classes from the branch state before the client changes.
- [ ] 5.2 Extend `java-client/specs/otis-api-1.1.0.yml` with the link paths and schemas from the backend-generated YAML. Verify that `./gradlew :java-client:openApiGenerate` produces a `LinksApi`.
- [ ] 5.3 (unit, red first) Test that `OtisClients.newApiClient()` deserializes an `AccountLinkDTO` sample and that `ProblemDetails.from` reads a `link-code-invalid` problem. Compare `javap -public` against 5.1: additions only. Build `./gradlew :velocity-plugin:shadowJar` without source changes.

## 6. Full build

- [ ] 6.1 Run `./gradlew clean build` and verify it is green. Tick all completed boxes in this file and push.

## 7. Pull Request

- [ ] 7.1 Main session reviews the diff and tests against `specs/account-links/spec.md`. Verify that every scenario maps to a passing test and that no code, externalId or displayName reaches logs or spans.
- [ ] 7.2 Open the pull request from `feat/account-links` against `main`, titled `feat(links): link external accounts to players with one-time codes`. The body includes:
  - the migration plan (V3, additive, rollback)
  - the security note (no service auth, internal only)
  - the compatibility evidence
  - a final line `https://claude.ai/code/session_012BmpdMdQ5wwagagQvfcq8S`

  **Do not merge.** Verify that `gh pr view` shows the PR as open.
