-- Account link events (additive): transactional outbox. A row is written in the same transaction as the
-- link change it describes and is published to Kafka asynchronously (at least once). No existing table is altered.
create table outbox_event (
    id uuid not null,
    aggregate_key varchar(36) not null,
    event_type varchar(64) not null,
    payload json not null,
    created_at datetime(6) not null,
    claimed_by varchar(64),
    claimed_until datetime(6),
    published_at datetime(6),
    attempts integer not null default 0,
    primary key (id)
) engine=InnoDB;
-- pending scan (published_at is null, ordered by created_at); its leading column also serves the retention delete
create index idx_outbox_event_pending on outbox_event (published_at, created_at);
