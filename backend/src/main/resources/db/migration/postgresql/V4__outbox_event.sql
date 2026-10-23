-- Account link events (additive): transactional outbox. A row is written in the same transaction as the
-- link change it describes and is published to Kafka asynchronously (at least once). No existing table is altered.
create table outbox_event (
    id uuid not null,
    aggregate_key varchar(36) not null,
    event_type varchar(64) not null,
    payload jsonb not null,
    created_at timestamp(6) with time zone not null,
    claimed_by varchar(64),
    claimed_until timestamp(6) with time zone,
    published_at timestamp(6) with time zone,
    attempts integer not null default 0,
    primary key (id)
);
-- pending scan (published_at is null, ordered by created_at); its leading column also serves the retention delete
create index idx_outbox_event_pending on outbox_event (published_at, created_at);
