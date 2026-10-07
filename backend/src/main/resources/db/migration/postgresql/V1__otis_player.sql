-- Baseline: the player table exactly as Hibernate generated it before Flyway was introduced.
-- Never runs on an existing database (it is baselined at version 1); it only shapes fresh databases.
create table if not exists otis_player (first_join bigint not null, last_join bigint not null, player_uuid uuid, uuid uuid not null, locale varchar(255) default 'en-US', player_name varchar(255), profile_texture jsonb, primary key (uuid));
create index if not exists idx_player_uuid on otis_player (player_uuid);
create index if not exists idx_player_name on otis_player (player_name);
