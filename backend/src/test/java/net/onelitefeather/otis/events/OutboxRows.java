package net.onelitefeather.otis.events;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads {@code outbox_event} rows over JDBC, independent of the repository under test. */
final class OutboxRows {

    /** One row; {@code payload} is the stored JSON text. */
    record Row(String id, String key, String type, String payload) {
    }

    private OutboxRows() {
    }

    /** @return the rows of one player, oldest first; players use random uuids, so tests do not see each other */
    static List<Row> of(DataSource dataSource, UUID player) {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id, aggregate_key, event_type, payload from outbox_event where aggregate_key = ? "
                             + "order by created_at, id")) {
            statement.setString(1, player.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot read outbox_event", e);
        }
        return rows;
    }

    static long countAll(DataSource dataSource) {
        try (Connection connection = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getConnection();
             PreparedStatement statement = connection.prepareStatement("select count(*) from outbox_event");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot count outbox_event", e);
        }
    }

    /** Removes all rows, so a test starts from an empty outbox (the relay claims every pending row). */
    static void clear(DataSource dataSource) {
        update(dataSource, "delete from outbox_event");
    }

    static boolean exists(DataSource dataSource, UUID id) {
        return scalar(dataSource, "select count(*) from outbox_event where id = '" + id + "'") == 1;
    }

    static long attempts(DataSource dataSource, UUID id) {
        return scalar(dataSource, "select attempts from outbox_event where id = '" + id + "'");
    }

    static long pending(DataSource dataSource) {
        return scalar(dataSource, "select count(*) from outbox_event where published_at is null");
    }

    private static void update(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot run " + sql, e);
        }
    }

    private static long scalar(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot run " + sql, e);
        }
    }
}
