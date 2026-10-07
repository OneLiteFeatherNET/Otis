-- Player settings (additive): one row per player and Adventure key; the value is opaque JSON.
create table player_setting (
    id uuid not null,
    player_id uuid not null,
    key_namespace varchar(255) not null,
    key_value varchar(255) not null,
    setting_value json not null,
    version bigint not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uq_player_setting_key unique (player_id, key_namespace, key_value),
    constraint fk_player_setting_player foreign key (player_id) references otis_player (uuid) on delete cascade
) engine=InnoDB;
create index idx_player_setting_namespace on player_setting (player_id, key_namespace);
