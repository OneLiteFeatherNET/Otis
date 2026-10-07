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
            assertEquals(List.of(List.of("version=1", "type=BASELINE"), List.of("version=2", "type=SQL")),
                    MigrationTestSupport.dumpColumns(url, "\"flyway_schema_history\" where \"version\" is not null", "\"installed_rank\"", "\"version\"", "\"type\""),
                    "V1 must be baselined (never executed) and only V2 applied");
        }
    }
}
