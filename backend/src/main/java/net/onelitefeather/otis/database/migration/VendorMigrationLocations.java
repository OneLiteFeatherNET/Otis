package net.onelitefeather.otis.database.migration;

import io.micronaut.context.annotation.Context;
import io.micronaut.flyway.FlywayConfigurationCustomizer;
import jakarta.inject.Named;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Points Flyway at the migration directory of the connected database vendor
 * ({@code db/migration/h2}, {@code postgresql} or {@code mariadb}).
 * <p>
 * Flyway has no {@code {vendor}} location placeholder (that is a Spring Boot feature) and the
 * column types differ per vendor (UUID, JSON, timestamps), so one customizer picks the directory.
 * It is the extension point {@code micronaut-flyway} provides for exactly this.
 */
@Context
@Named("default")
public final class VendorMigrationLocations implements FlywayConfigurationCustomizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VendorMigrationLocations.class);
    private static final String BASE = "classpath:db/migration/";

    @Override
    public void customizeFluentConfiguration(FluentConfiguration configuration) {
        String vendor;
        try (Connection connection = configuration.getDataSource().getConnection()) {
            vendor = directoryFor(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not determine the database vendor for the migrations", exception);
        }
        LOGGER.debug("Using migration directory {}", vendor);
        configuration.locations(BASE + vendor);
    }

    /**
     * @param productName the JDBC database product name, e.g. {@code PostgreSQL}
     * @return the migration directory name for that product
     * @throws IllegalArgumentException if no migrations exist for the product
     */
    public static String directoryFor(String productName) {
        return switch (productName.toLowerCase(Locale.ROOT)) {
            case "h2" -> "h2";
            case "postgresql" -> "postgresql";
            case "mariadb", "mysql" -> "mariadb";
            default -> throw new IllegalArgumentException("No migrations for database product: " + productName);
        };
    }

    @Override
    public String getName() {
        return "default";
    }
}
