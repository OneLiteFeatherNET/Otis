# Proposal

Ships as: `feat(links): publish account link events to kafka via outbox`

## Why

When a player links or unlinks an account, other services have to react: the Discord bot assigns roles, the lobby grants a link reward, a website refreshes the profile. Without events each of them would have to poll Otis. Publishing link changes to Kafka lets those services react independently and lets new services join without changes to Otis. A transactional outbox prevents two failure cases: an event for a link that was never stored, and a stored link without an event.

## What Changes

- Every link that is created, upgraded to verified or unlinked through the links API writes an event row to an outbox table, in the same database transaction as the link change.
- Known limitation: deleting a whole player through the legacy endpoint `POST /otis/delete/{owner}` removes that player's links by database cascade and emits no events, because the legacy endpoint stays unchanged.
- A scheduled relay publishes pending outbox rows to the Kafka topic `otis.account-links`:
  - Each event is a CloudEvents 1.0 structured JSON message with type `net.onelitefeather.otis.account.linked` or `net.onelitefeather.otis.account.unlinked`.
  - The message key is the player uuid, which keeps the order per player.
  - Delivery is at least once; consumers deduplicate by the event `id`.
- Events carry only identifiers and facts: player uuid, provider, externalId, verified, time. They never carry link codes or tokens.
- Otis creates its topic at startup (3 partitions, replication 3), because the cluster has automatic topic creation disabled and runs no topic operator.
- Kafka is optional:
  - `KAFKA_ENABLED` defaults to `false`. Then no Kafka clients start and no outbox rows are written, so local development and CI need no Kafka.
  - Published rows are deleted after 7 days.
- Producer spans with W3C `traceparent` headers come from Micronaut's Kafka tracing integration. A gauge `otis.outbox.pending` shows whether events are stuck.
- A separate pull request in the Kubernetes-FLUX repository enables Kafka for prod Otis. It may only be merged after an Otis release containing this change is deployed.

## Capabilities

### New Capabilities
- `account-link-events`: the event contract for account link changes (format, topic, key, delivery guarantee, privacy), the transactional outbox, and the enable toggle.

### Modified Capabilities

## Impact

- **Backend:**
  - outbox entity and repository, event factory, relay (`@Scheduled`), publisher interface with a Kafka implementation
  - the link service writes outbox rows through the `LinkTransactions` helper from `add-account-links`
- **Database:** Flyway V4, additive only, creates the table `outbox_event` for PostgreSQL, MariaDB and H2.
- **New dependencies:**
  - `io.micronaut.kafka:micronaut-kafka` (version from the Micronaut platform catalog)
  - `io.micronaut.tracing:micronaut-tracing-opentelemetry-kafka` (platform catalog)
  - for CloudEvents, no library: the envelope is a small record serialized by Micronaut Serde
- **Configuration:** `KAFKA_ENABLED` (default `false`), `KAFKA_BOOTSTRAP_SERVERS`.
- **Kubernetes-FLUX:** prod Otis HelmRelease env `KAFKA_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS=feather-kafka-kafka-bootstrap.kafka.svc:9092`, as a separate PR that is not merged.
- **Kafka cluster:** a new topic `otis.account-links` on `feather-kafka`, shared with Sentry. It has no effect on Sentry's topics.
- **API / client / plugin:** unchanged.
- **User-facing text:** none.
- **Depends on:** `add-account-links`.
