# Design

## Context

See proposal.md - Why. This builds on `add-account-links`: `AccountLinkService` changes links inside `LinkTransactions` (explicitly qualified programmatic transactions, because the declarative `@Transactional` is ambiguous with two `@Primary` transaction operations beans).

The cluster runs Strimzi Kafka `feather-kafka` 4.2 (namespace `kafka`):
- plain internal listener `feather-kafka-kafka-bootstrap.kafka.svc:9092`, no authentication
- `auto.create.topics.enable=false`
- no entity/topic operator, deliberately, because Sentry's 116 topics are managed by `topicctl`
- replication factor 3, `min.insync.replicas` 2

Prod Otis runs 2 replicas. CI has no Docker, so tests cannot start a Kafka broker. Micronaut tracing already instruments HTTP and JDBC.

## Goals / Non-Goals

**Goals:**
- No lost and no phantom events (outbox).
- Per-player ordering.
- Safe with 2 replicas.
- Zero impact when disabled.

**Non-Goals:**
- Exactly-once delivery.
- Consumers (Discord bot, lobby).
- Events for legacy player deletion.
- Schema registry.
- Events for settings.

## Decisions

### D1 - Transactional outbox table `outbox_event` (Flyway V4)

**Columns:**

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK = CloudEvents `id` |
| `aggregate_key` | varchar(36) | player uuid, used as Kafka key |
| `event_type` | varchar(64) | |
| `payload` | JSON | `jsonb` / `JSON`; the serialized CloudEvent |
| `created_at` | timestamp with time zone | |
| `claimed_by` | varchar(64) | nullable |
| `claimed_until` | timestamp with time zone | nullable |
| `published_at` | timestamp with time zone | nullable |
| `attempts` | int | |

**Indexes:**
- `(published_at, created_at)` for the pending scan
- `(published_at)` for retention

**Writing the row:**
- The row is written by `AccountLinkService` through an `OutboxWriter` port, inside the existing `LinkTransactions` block that changes the link.
- With publishing disabled, the bean is a `NoopOutboxWriter`.
- Built-in option considered: emitting directly to Kafka after commit (a Micronaut `@TransactionalEventListener`). Rejected: a crash or a Kafka outage between commit and send loses the event, which is the dual-write problem.

**Tests:**
- Integration on H2: link with publishing enabled → row exists. Rollback after the claim → no row.
- Disabled → no row.

**SOLID:**
- DIP: the service depends on the `OutboxWriter` port.
- OCP: the noop/real writer is chosen by configuration.

### D2 - CloudEvents envelope as a record, serialized by Micronaut Serde

- A `@Serdeable` record `CloudEvent(specversion, id, source, type, subject, time, datacontenttype, data)` with a `LinkEventData` record.
- Built-in option considered: the CloudEvents Java SDK (`io.cloudevents:cloudevents-kafka`). Rejected: it adds three artifacts and its own Jackson integration for an envelope of 8 fields. Structured mode is plain JSON, which Serde already handles.

**Tests:** unit tests on the JSON output (field names, `specversion` 1.0, `data` without code/display name/value, `externalId` null for unverified).

**SOLID:** SRP.

### D3 - Kafka via `micronaut-kafka`; topic created by a `NewTopic` bean

- `@KafkaClient` producer interface `LinkEventProducer` with `@KafkaKey String key` and a String JSON payload.
- Producer config: `acks=all`, `enable.idempotence=true`.
- A `NewTopic("otis.account-links", 3, (short) 3)` bean. Micronaut Kafka creates declared topics through the AdminClient at startup and does not alter existing ones (verify against the module docs in the first task; if this is not supported, an explicit `AdminClient.createTopics` call that ignores `TopicExistsException`).
- Built-in first: Micronaut Kafka instead of the raw Kafka client.
- Alternative: topic via `topicctl` or a Strimzi `KafkaTopic` CR. Rejected: there is no topic operator, and `topicctl` belongs to Sentry's setup.

**Enable toggle:**
- `kafka.enabled: ${KAFKA_ENABLED:false}`, which is Micronaut Kafka's own switch, plus `@Requires(property = "otis.events.enabled", value = "true")` on the relay, the real writer and the topic bean. `otis.events.enabled` mirrors `KAFKA_ENABLED`.
- Test environment: publishing enabled with a fake `EventPublisher` and `kafka.enabled=false`.

**Tests:** context tests:
- disabled → no `KafkaProducer` / relay beans
- enabled with fake → relay bean present

**SOLID:** DIP. The relay depends on `EventPublisher`; `KafkaEventPublisher` wraps the `@KafkaClient`.

### D4 - Relay: `@Scheduled` with per-row lease claim

**Each run** (`fixedDelay = 2s`, `initialDelay = 10s`):
1. Select up to 100 candidate ids: `published_at IS NULL AND (claimed_until IS NULL OR claimed_until < now)`, ordered by `created_at`.
2. Claim each one with a conditional update: `UPDATE outbox_event SET claimed_by = :instance, claimed_until = :now + 30s, attempts = attempts + 1 WHERE id = :id AND published_at IS NULL AND (claimed_until IS NULL OR claimed_until < :now)`. Affected rows = 1 means this instance owns the row.
3. Send the claimed rows in `created_at` order, waiting for the broker ack.
4. Set `published_at`.

**On failure:**
- Stop the run. The lease expires, and the next run retries in order.
- Per-player order holds, because the same key is never sent ahead of an earlier unpublished row of that key: rows are processed strictly by `created_at`, and sending stops at the first failure.

**Retention:** a separate `@Scheduled(cron = "0 15 * * * *")` deletes rows with `published_at < now - 7 days`.

**Why this claim mechanism:**
- The per-id conditional update works identically on PostgreSQL, MariaDB and H2.
- Alternatives rejected:
  - `FOR UPDATE SKIP LOCKED`: not portable to the H2 used in tests.
  - A single `UPDATE ... WHERE id IN (SELECT ... FROM outbox_event)`: MariaDB rejects a subquery on the target table (error 1093).
- Instance id: `HOSTNAME` env (the pod name) or a random uuid.

**Tests:**
- Integration on H2 with a fake publisher and a fixed `Clock`:
  - publishes in order and marks rows published
  - a failing publisher leaves rows unpublished, and the next run retries
  - an expired lease is reclaimed
  - retention deletes only rows older than 7 days
- Concurrency: two relay instances with different ids run against 10 rows on virtual threads, joined. Each row is published once.

**SOLID:** SRP. Claim/publish/retention are separate methods; the scheduler is only a trigger.

### D5 - Observability

**Spans:**
- Producer spans and `traceparent` injection come from `micronaut-tracing-opentelemetry-kafka` (built-in first; no own producer spans).
- The relay run gets one INTERNAL span `outbox.relay` with attributes `otis.outbox.claimed` and `otis.outbox.published` (counts). It is ERROR only on an unexpected failure, recorded once.
- Spans are not created per row.

**Metric:**
- `otis.outbox.pending`: observable gauge via the OTel API from the injected `OpenTelemetry`, unit `{event}`, description "Outbox events not yet published to Kafka", no attributes (cardinality 1).
- The callback reads a cached count that is updated by each relay run (cheap, non-blocking).
- It answers "are link events stuck?" and is a candidate for a Grafana alert later.

**Logs:**
- WARN once per failure burst (on the first failed run after a success), with the exception, without payload.
- INFO once when the relay starts, with the topic.
- DEBUG per run with counts.

**Tests:**
- `OpenTelemetryExtension`: gauge value equals the pending rows.
- Relay span attributes.
- WARN logged once over two consecutive failing runs (captured appender).

## Risks / Trade-offs

- **[Duplicate delivery, e.g. a crash after send and before `published_at`]** → At-least-once is documented, and consumers deduplicate by CloudEvents `id`.
- **[A poison row blocks the per-order relay]** → `attempts` is visible, and a stuck backlog shows in `otis.outbox.pending`. Payloads are built at write time, so a send failure is a broker problem, not a data problem.
- **[Shared Kafka cluster with Sentry]** → It is a dedicated topic with no config changes to the cluster; the volume is a few events per day.
- **[Topic auto-creation by an app]** → Only `otis.account-links`, created idempotently. The cluster setting `auto.create.topics.enable=false` stays in force for everything else.
- **[Legacy player deletion emits no events]** → Documented limitation. Consumers can reconcile via the lookup API. A follow-up can route deletion through a service.

## Migration Plan

1. Release an Otis version containing V4. `outbox_event` is created, and with `KAFKA_ENABLED` unset nothing else changes.
2. Merge the separate Kubernetes-FLUX PR (`KAFKA_ENABLED=true`, bootstrap servers) after step 1 is deployed. On start, Otis creates the topic and begins relaying.
3. Rollback: set `KAFKA_ENABLED=false` (pending rows stay in the table and are harmless), or deploy the previous image, which ignores the table. Optional cleanup: `DROP TABLE outbox_event;` and deleting the topic `otis.account-links`.

## Implementation Notes

Deviations found during implementation:
- **micronaut-kafka 5.9.0 has no `kafka.enabled` switch** (checked in the jar: no such property, the module only knows `kafka.health.enabled`). The design's "Micronaut Kafka's own switch" does not exist. Instead every Kafka bean of Otis (`LinkEventProducer`, `KafkaEventPublisher`, the `NewTopic` factory) carries the meta annotation `@WhenKafkaPublishing` (`otis.events.enabled=true` and `otis.events.publisher=kafka`, default `kafka`). `KAFKA_ENABLED` feeds `otis.events.enabled` and `kafka.health.enabled`: without the latter the Kafka health indicator would probe `localhost:9092` and turn `/health` DOWN. Tests enable events with `otis.events.publisher=fake`.
- **`NewTopic` beans are created automatically.** micronaut-kafka's `KafkaNewTopics` (a `@Context` bean that only exists when at least one `org.apache.kafka.clients.admin.NewTopic` bean exists) calls `AdminClient.createTopics` at startup. The result is not awaited, so `TopicExistsException` for an existing topic is ignored and the topic stays unchanged; no own AdminClient code is needed. Limitation: if Kafka is unreachable at startup, the creation fails silently and is not retried until the next start.
- Only one index, `(published_at, created_at)`: its leading column serves the retention delete, so the second index of D1 would be redundant.
- `AccountLinkService.delete` and `putUnverified` now run in a `LinkTransactions` block (delete needs the removed link for the event; both must write the outbox row in the transaction of the change).
- The relay sets short producer timeouts (`max.block.ms` 5s, `request.timeout.ms` 10s, `delivery.timeout.ms` 15s), so an unreachable broker fails a run quickly. A timed-out send may still arrive later; that is a duplicate with the same event id.
- The instance id comes from `otis.events.instance-id` (`HOSTNAME`), else a random uuid. Relay interval and initial delay are configurable (`otis.events.relay.interval`, `otis.events.relay.initial-delay`) so tests keep the scheduler idle.
- `otis.outbox.pending` is an OpenTelemetry gauge as designed. The backend sets `otel.metrics.exporter: none` (metrics go through Micrometer/Prometheus), so the gauge is visible in tests but not exported in production until an OTel metrics exporter is configured. Exposing it through Micrometer as well is a follow-up.
- The broker path was verified against throwaway containers (3-broker Kafka 3.9.1 KRaft with `auto.create.topics.enable=false` and `min.insync.replicas=2`, PostgreSQL 17; removed afterwards): Flyway applied V1-V4 on PostgreSQL (`payload` is `jsonb`); at startup the topic `otis.account-links` was created with 3 partitions and replication factor 3; redeem, unverified put and unlink produced three messages with the player uuid as key and a `traceparent` header, all rows were marked published. Against MariaDB the V4 script was not executed (no MariaDB available).
