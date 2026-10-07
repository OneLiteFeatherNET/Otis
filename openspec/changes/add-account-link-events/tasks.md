# Tasks

## Execution Plan

Branch `feat/account-link-events` from `origin/feat/account-links`; the PR base is `feat/account-links`. All agents are Sonnet in their own worktree.

Every agent prompt restates these rules:
- **Built-in first:** Micronaut Kafka, Micronaut tracing for Kafka, Serde records instead of the CloudEvents SDK.
- **Java 25:** records, sealed results, virtual threads in concurrency tests.
- **i18n:** no player-facing text.
- **SLF4J:** WARN once per failure burst, never payloads.
- **OpenTelemetry:** API only, injected `OpenTelemetry`; tests with `OpenTelemetryExtension`.
- **Tests:** F.I.R.S.T. with an injected `Clock` and no sleeps (drive the relay by calling its run method, not by waiting on the scheduler); test-first.
- **Commits:** Conventional Commits with the `Claude-Session` trailer. **Merge nothing.**

| Wave | Agent | Task IDs | Model | May Touch | Must Not Touch |
| ---- | ----- | -------- | ----- | --------- | -------------- |
| 1 | backend-events | 1.1-1.2, 2.1, 3.1-3.3, 4.1-4.3, 5.1-5.2 | sonnet | `backend/**`, `settings.gradle.kts`, `openspec/changes/add-account-link-events/**` | `java-client/**`, `velocity-plugin/**`, existing controllers |
| 2 | flux-events | 6.1 | sonnet | Kubernetes-FLUX worktree: `products/otis/clusters/feather-core/otis/release.yaml` | everything else in Kubernetes-FLUX |
| 3 | main session | 7.1-7.2 | - | PR metadata | source code |

## 1. Setup

- [ ] 1.1 Copy `openspec/changes/add-account-link-events/` from `/mnt/projects/oss/onelitefeather/Otis` into the worktree and commit it as `docs(openspec): add add-account-link-events change`. Verify `openspec validate add-account-link-events --strict` passes.
- [ ] 1.2 Add `micronaut-kafka` and `micronaut-tracing-opentelemetry-kafka` (platform catalog versions) and set `kafka.enabled: ${KAFKA_ENABLED:false}`, `otis.events.enabled: ${KAFKA_ENABLED:false}` and `kafka.bootstrap.servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`. Verify an application context starts without Kafka (`./gradlew :backend:test` green), and confirm from the module documentation how declared `NewTopic` beans are created (D3).

## 2. Schema

- [ ] 2.1 (integration, red first) Extend the fresh-database and existing-database migration tests for `outbox_event`. Write V4 for `h2`, `postgresql` and `mariadb` (D1). Verify the tests pass and the existing rows are unchanged.

## 3. Outbox write

- [ ] 3.1 (unit, red first) Tests for the CloudEvent records (D2): JSON field names, `specversion` 1.0, `type` per change, `subject` = player uuid, and `data` without code, display name or unverified value. Implement and verify.
- [ ] 3.2 (integration, red first) With publishing enabled (fake publisher):
  - redeem, upgrade, unverified set and unlink each write exactly one row
  - a no-op delete and a 409 redeem write none
  - an exception after the code claim rolls back both the link and the outbox row

  Implement `OutboxWriter` within `LinkTransactions` and verify.
- [ ] 3.3 (integration, red first) With publishing disabled (default config): creating a link writes no outbox row and no Kafka beans exist. Implement `NoopOutboxWriter` and the `@Requires` wiring, and verify.

## 4. Relay

- [ ] 4.1 (integration, red first) Relay tests on H2 with a fake `EventPublisher` and a fixed `Clock`, calling the run method directly:
  - publishes in `created_at` order with key = player uuid and marks rows published
  - a failing publisher stops the run and the next run retries
  - an expired lease is reclaimed
  - retention deletes only rows published more than 7 days ago

  Implement D4 and verify.
- [ ] 4.2 (integration, red first) Two relay instances with different ids against 10 pending rows on virtual threads, joined: every row is published exactly once. Verify.
- [ ] 4.3 Implement `KafkaEventPublisher` (`@KafkaClient`, `acks=all`, idempotence) and the topic bean (`otis.account-links`, 3 partitions, RF 3). Verify by a context test with `KAFKA_ENABLED=true`: the beans exist and the producer is configured (no broker needed). Record in the PR that the real broker path is verified after deployment.

## 5. Observability and build

- [ ] 5.1 (unit, red first) `OpenTelemetryExtension` tests:
  - `otis.outbox.pending` gauge (name, unit `{event}`, value = pending rows, no attributes)
  - `outbox.relay` span attributes
  - WARN logged once over two consecutive failing runs (captured appender)

  Implement D5 and verify.
- [ ] 5.2 Run `./gradlew clean build`. Verify it is green, that `ExistingEndpointsCompatibilityTest`, the settings tests and the link tests still pass, and that the client `javap` output is unchanged. Tick the boxes and push the branch.

## 6. Deployment config (separate repository)

- [ ] 6.1 In `/mnt/projects/oss/onelitefeather/Kubernetes-FLUX`, create a worktree on a new branch `feat/otis-kafka-events` from `origin/main`. In `products/otis/clusters/feather-core/otis/release.yaml`, add the env entries `KAFKA_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS=feather-kafka-kafka-bootstrap.kafka.svc:9092`, following the file's existing `env` list. Commit `feat(otis): enable account link events on kafka` with the `Claude-Session` trailer, push, and open a PR. The PR body states that it must be merged only after an Otis release containing `add-account-link-events` is deployed, and ends with `https://claude.ai/code/session_012BmpdMdQ5wwagagQvfcq8S`. **Do not merge.** Verify the PR checks pass, then remove the worktree.

## 7. Pull Request

- [ ] 7.1 Main session reviews the diff and tests against `specs/account-link-events/spec.md`. Verify that every scenario maps to a passing test and that no payload, code or display name is logged.
- [ ] 7.2 Open the pull request from `feat/account-link-events` against base `feat/account-links`, titled `feat(links): publish account link events to kafka via outbox`. The body includes:
  - the event contract
  - the migration plan (V4, toggle, FLUX PR ordering)
  - a link to the FLUX PR
  - a final line `https://claude.ai/code/session_012BmpdMdQ5wwagagQvfcq8S`

  **Do not merge.** Verify that `gh pr view` shows the PR as open.
