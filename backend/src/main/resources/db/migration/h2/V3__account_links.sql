-- Account links (additive): verified and unverified links of a player to external accounts, and the
-- one-time link codes (stored as SHA-256 hash only) that verify them. No existing table is altered.
create table account_link (
    id uuid not null,
    player_id uuid not null,
    provider varchar(16) not null,
    external_id varchar(64),
    link_value varchar(200),
    display_name varchar(100),
    verified boolean not null,
    linked_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uq_account_link_player_provider unique (player_id, provider),
    constraint uq_account_link_provider_external unique (provider, external_id),
    constraint fk_account_link_player foreign key (player_id) references otis_player (uuid) on delete cascade
);

create table link_code (
    id uuid not null,
    player_id uuid not null,
    provider varchar(16) not null,
    code_hash char(64) not null,
    created_at timestamp(6) with time zone not null,
    expires_at timestamp(6) with time zone not null,
    used_at timestamp(6) with time zone,
    revoked_at timestamp(6) with time zone,
    primary key (id),
    constraint uq_link_code_hash unique (code_hash),
    constraint fk_link_code_player foreign key (player_id) references otis_player (uuid) on delete cascade
);
create index idx_link_code_player_created on link_code (player_id, created_at);
