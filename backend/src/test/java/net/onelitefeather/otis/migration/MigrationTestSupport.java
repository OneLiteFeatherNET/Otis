package net.onelitefeather.otis.migration;

import io.micronaut.context.ApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helpers for migration tests: every test gets its own in-memory H2 database (unique name), so tests
 * share no state and can run in any order.
 */
final class MigrationTestSupport {

    private MigrationTestSupport() {
    }

    /** @return a JDBC URL of a fresh, uniquely named in-memory database that survives connection closes */
    static String uniqueDatabaseUrl() {
        return "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    /** Application context against {@code url}, Hibernate in {@code validate} mode so mapping and DDL must agree. */
    static ApplicationContext startApplication(String url) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.default.url", url);
        properties.put("datasources.default.username", "sa");
        properties.put("datasources.default.password", "");
        properties.put("datasources.default.driver-class-name", "org.h2.Driver");
        properties.put("datasources.default.dialect", "H2");
        properties.put("jpa.default.properties.hibernate.hbm2ddl.auto", "validate");
        ApplicationContext context = ApplicationContext.run(properties, "test");
        // the entity manager factory validates the schema on creation
        context.getBean(jakarta.persistence.EntityManagerFactory.class);
        return context;
    }

    static void runScript(String url, String classpathResource) throws SQLException, IOException {
        try (InputStream in = MigrationTestSupport.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + classpathResource);
            }
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                 Statement statement = connection.createStatement()) {
                for (String sql : script.split(";\\s*\\n")) {
                    String trimmed = sql.lines().filter(line -> !line.startsWith("--")).reduce("", (a, b) -> a + b + "\n").trim();
                    if (!trimmed.isEmpty()) {
                        statement.execute(trimmed);
                    }
                }
            }
        }
    }

    /** All rows of {@code table} as strings, ordered by {@code orderBy}, for exact before/after comparison. */
    static List<List<String>> dumpRows(String url, String table, String orderBy) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from " + table + " order by " + orderBy)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) {
                    row.add(rs.getMetaData().getColumnName(i) + "=" + rs.getString(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** Selected {@code columns} of every row of {@code table}, as {@code column=value} strings. */
    static List<List<String>> dumpColumns(String url, String table, String orderBy, String... columns) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select " + String.join(", ", columns) + " from " + table + " order by " + orderBy)) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 0; i < columns.length; i++) {
                    row.add(columns[i].replace("\"", "") + "=" + rs.getString(i + 1));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    static boolean tableExists(String url, String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             ResultSet rs = connection.getMetaData().getTables(null, null, table.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
