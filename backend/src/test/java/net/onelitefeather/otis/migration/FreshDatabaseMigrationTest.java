package net.onelitefeather.otis.migration;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scenario "Fresh database": both tables are created by the migrations and match the entity mapping. */
class FreshDatabaseMigrationTest {

    @Test
    void migrationsCreatePlayerAndSettingStorageThatHibernateAccepts() throws Exception {
        String url = MigrationTestSupport.uniqueDatabaseUrl();

        // startApplication fails if Hibernate's schema validation rejects the migrated schema
        try (ApplicationContext ignored = MigrationTestSupport.startApplication(url)) {
            assertTrue(MigrationTestSupport.tableExists(url, "otis_player"), "otis_player must exist after migration");
            assertTrue(MigrationTestSupport.tableExists(url, "player_setting"), "player_setting must exist after migration");
            assertTrue(MigrationTestSupport.tableExists(url, "account_link"), "account_link must exist after migration");
            assertTrue(MigrationTestSupport.tableExists(url, "link_code"), "link_code must exist after migration");
        }
    }
}
