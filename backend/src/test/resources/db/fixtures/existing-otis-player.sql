-- Schema as created by Hibernate (hbm2ddl=update) before Flyway was introduced, plus sample rows.
create table otis_player (first_join bigint not null, last_join bigint not null, player_uuid uuid, uuid uuid not null, locale varchar(255) default 'en-US', player_name varchar(255), profile_texture json, primary key (uuid));
create index idx_player_uuid on otis_player (player_uuid);
create index idx_player_name on otis_player (player_name);
insert into otis_player (first_join, last_join, player_uuid, uuid, locale, player_name, profile_texture) values (1000, 2000, '11111111-1111-4111-8111-111111111111', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'de-DE', 'Alice_One', '{"skin":"abc","cape":null}' format json);
insert into otis_player (first_join, last_join, player_uuid, uuid, locale, player_name, profile_texture) values (3000, 4000, '22222222-2222-4222-8222-222222222222', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'en-US', 'Bob_Two', null);
insert into otis_player (first_join, last_join, player_uuid, uuid, locale, player_name, profile_texture) values (5000, 6000, null, 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', null, null, '{}' format json);
