# Spec Delta

## Purpose

Publishes every change to a player's account links as an event on Kafka, so that services such as the Discord bot or the lobby can react to links and unlinks without polling Otis and without ever seeing link codes or tokens.

## ADDED Requirements

### Requirement: Link changes produce events
When event publishing is enabled, the system SHALL produce exactly one event per successful link change made through the links API:
- `net.onelitefeather.otis.account.linked` when a link is created or an unverified link is upgraded to verified by redeeming a code, or when an unverified profile link is set
- `net.onelitefeather.otis.account.unlinked` when an existing link is deleted

Requests that change nothing (deleting a missing link) or fail SHALL NOT produce events. Deleting a player through the legacy endpoint `POST /otis/delete/{owner}` SHALL NOT produce events.

#### Scenario: Redeem produces linked event
- **WHEN** a player redeems a `discord` code with externalId `123456789012345678`
- **THEN** exactly one `net.onelitefeather.otis.account.linked` event for that player and provider is published

#### Scenario: Unlink produces unlinked event
- **WHEN** a player with a `discord` link deletes it
- **THEN** exactly one `net.onelitefeather.otis.account.unlinked` event is published

#### Scenario: No-op delete produces nothing
- **WHEN** a player without a `twitch` link deletes `twitch`
- **THEN** no event is published

#### Scenario: Failed redeem produces nothing
- **WHEN** a redeem is rejected with status 409
- **THEN** no event is published

### Requirement: Event format
Each event SHALL be a CloudEvents 1.0 structured-mode JSON message with:
- `specversion` `1.0`
- `id`: a unique uuid per event
- `source` `/otis`
- `type` as above
- `subject`: the player's Mojang uuid
- `time`: when the change happened
- `datacontenttype` `application/json`
- `data` containing `playerUuid`, `provider`, `externalId` (null for unverified links), `verified` and `occurredAt`

Events SHALL NOT contain link codes, code hashes, link values of unverified links, display names or any provider token.

#### Scenario: Linked event payload
- **WHEN** a `discord` link with externalId `123456789012345678` is created for player P
- **THEN** the event has `type` `net.onelitefeather.otis.account.linked`, `subject` P, and `data` `{playerUuid: P, provider: "discord", externalId: "123456789012345678", verified: true, occurredAt: <time>}`

#### Scenario: No secrets in events
- **WHEN** any link event is published
- **THEN** the message contains neither the link code, its hash, a display name nor an unverified link value

### Requirement: Topic, key and ordering
Events SHALL be published to the topic `otis.account-links` with the player's Mojang uuid as message key, so that all events of one player keep their order. The system SHALL create the topic at startup with 3 partitions and replication factor 3 if it does not exist, and SHALL leave an existing topic unchanged.

#### Scenario: Key is player uuid
- **WHEN** an event for player P is published
- **THEN** the Kafka message key is the string form of P

#### Scenario: Topic created once
- **WHEN** Otis starts with events enabled and the topic does not exist
- **THEN** the topic `otis.account-links` exists with 3 partitions afterwards

### Requirement: Transactional outbox and at-least-once delivery
The system SHALL store each event in the database in the same transaction as the link change it describes. An event SHALL therefore exist if and only if the change is stored. The system SHALL publish stored events to Kafka asynchronously with at-least-once delivery. An event that could not be published SHALL be retried until it is published. The same event MAY be delivered more than once with the same `id`, and consumers SHALL deduplicate by `id`. With several Otis replicas running, each event SHALL be published by one replica at a time. Published events SHALL be removed from the database 7 days after publishing.

#### Scenario: Rolled-back change has no event
- **WHEN** a redeem fails after the code was claimed and the transaction is rolled back
- **THEN** no event row exists for it

#### Scenario: Kafka temporarily unavailable
- **WHEN** Kafka is unreachable while a link is created and becomes reachable later
- **THEN** the link is stored immediately and the event is published after Kafka is reachable again

#### Scenario: Two replicas do not double-claim
- **WHEN** two relay instances run concurrently against the same database with 10 pending events
- **THEN** each event is claimed by exactly one instance in that run

#### Scenario: Retention
- **WHEN** an event was published more than 7 days ago
- **THEN** its row is deleted by the next cleanup

### Requirement: Publishing can be disabled
Event publishing SHALL be controlled by `KAFKA_ENABLED`, default `false`. When disabled, the system SHALL NOT connect to Kafka, SHALL NOT write outbox rows, and the links API SHALL behave exactly as without this change.

#### Scenario: Disabled by default
- **WHEN** Otis starts without `KAFKA_ENABLED`
- **THEN** no Kafka client is created and creating a link writes no outbox row

#### Scenario: Enabled
- **WHEN** Otis starts with `KAFKA_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS` set
- **THEN** link changes write outbox rows and the relay publishes them

### Requirement: Event pipeline is observable
The system SHALL expose a Micrometer gauge `otis.outbox.pending` (exported on `/prometheus`), without tags, holding the number of unpublished events. Publishing SHALL create producer spans that carry the W3C trace context into the message headers. A relay run that fails to publish SHALL be logged once at WARN without event payloads.

#### Scenario: Backlog visible
- **WHEN** 3 events are stored and not yet published
- **THEN** `otis.outbox.pending` reports 3

#### Scenario: Trace context propagated
- **WHEN** an event is published while tracing is enabled
- **THEN** the Kafka message carries a `traceparent` header

### Requirement: Storage stays compatible
The change SHALL only add the outbox table; existing tables and endpoints SHALL stay unchanged.

#### Scenario: Upgrade keeps data
- **WHEN** the new version starts against a database with players, settings and links
- **THEN** all existing rows are unchanged and the table `outbox_event` exists
