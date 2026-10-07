package net.onelitefeather.otis.migration;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario "Upgrade existing database": a database created before Flyway (no history table) is
 * baselined, keeps every row unchanged and gains the settings storage.
 */
class ExistingDatabaseMigrationTest {

    private static final String FIXTURE = "/db/fixtures/existing-otis-player.sql";
    private static final String ORDER = "uuid";

    @Test
    void existingPlayerRowsStayIdenticalAndSettingStorageIsAdded() throws Exception {
        String url = MigrationTestSupport.uniqueDatabaseUrl();
        MigrationTestSupport.runScript(url, FIXTURE);
        List<List<String>> before = MigrationTestSupport.dumpRows(url, "otis_player", ORDER);
        assertFalse(before.isEmpty(), "fixture must contain players");
        assertFalse(MigrationTestSupport.tableExists(url, "player_setting"), "precondition: no settings storage yet");

        try (ApplicationContext ignored = MigrationTestSupport.startApplication(url)) {
            assertEquals(before, MigrationTestSupport.dumpRows(url, "otis_player", ORDER),
                    "every existing player row must be unchanged after the upgrade");
            assertTrue(MigrationTestSupport.tableExists(url, "player_setting"), "player_setting must exist after the upgrade");
            assertTrue(MigrationTestSupport.tableExists(url, "account_link"), "account_link must exist after the upgrade");
            assertTrue(MigrationTestSupport.tableExists(url, "link_code"), "link_code must exist after the upgrade");
            assertEquals(List.of(List.of("version=1", "type=BASELINE"), List.of("version=2", "type=SQL"), List.of("version=3", "type=SQL")),
                    MigrationTestSupport.dumpColumns(url, "\"flyway_schema_history\" where \"version\" is not null", "\"installed_rank\"", "\"version\"", "\"type\""),
                    "V1 must be baselined (never executed) and only V2 and V3 applied");
        }
    }

    @Test
    void playerAndSettingRowsOfAV2DatabaseStayIdenticalWhenLinkStorageIsAdded() throws Exception {
        String url = MigrationTestSupport.uniqueDatabaseUrl();
        MigrationTestSupport.runScript(url, FIXTURE);
        MigrationTestSupport.migrateUpTo(url, "2");
        MigrationTestSupport.execute(url, "insert into player_setting (id, player_id, key_namespace, key_value, setting_value, version, updated_at) "
                + "values ('99999999-9999-4999-8999-999999999999', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'lobby', 'player_hider', "
                + "'{\"enabled\":true}' format json, 1, timestamp with time zone '2026-01-01 00:00:00+00')");
        assertFalse(MigrationTestSupport.tableExists(url, "account_link"), "precondition: no link storage at V2");
        List<List<String>> players = MigrationTestSupport.dumpRows(url, "otis_player", ORDER);
        List<List<String>> settings = MigrationTestSupport.dumpRows(url, "player_setting", "id");
        assertFalse(settings.isEmpty(), "precondition: the V2 database holds a setting");

        try (ApplicationContext ignored = MigrationTestSupport.startApplication(url)) {
            assertEquals(players, MigrationTestSupport.dumpRows(url, "otis_player", ORDER), "player rows must be unchanged by V3");
            assertEquals(settings, MigrationTestSupport.dumpRows(url, "player_setting", "id"), "setting rows must be unchanged by V3");
            assertTrue(MigrationTestSupport.tableExists(url, "account_link"), "account_link must exist after V3");
            assertTrue(MigrationTestSupport.tableExists(url, "link_code"), "link_code must exist after V3");
            assertEquals(List.of(List.of("version=3", "type=SQL")),
                    MigrationTestSupport.dumpColumns(url, "\"flyway_schema_history\" where \"version\" = '3'", "\"installed_rank\"", "\"version\"", "\"type\""),
                    "V3 must be applied exactly once on top of V2");
        }
    }
}
