package net.onelitefeather.otis.migration;

import net.onelitefeather.otis.database.migration.VendorMigrationLocations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VendorMigrationLocationsTest {

    @ParameterizedTest
    @CsvSource({"H2,h2", "PostgreSQL,postgresql", "MariaDB,mariadb", "MySQL,mariadb"})
    void mapsJdbcProductNameToMigrationDirectory(String productName, String directory) {
        assertEquals(directory, VendorMigrationLocations.directoryFor(productName),
                "migration directory for " + productName);
    }

    @Test
    void rejectsUnknownDatabaseProduct() {
        assertThrows(IllegalArgumentException.class, () -> VendorMigrationLocations.directoryFor("Oracle"),
                "an unsupported database must fail fast instead of running without migrations");
    }
}
