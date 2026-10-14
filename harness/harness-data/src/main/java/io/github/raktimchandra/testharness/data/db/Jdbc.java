package io.github.raktimchandra.testharness.data.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal parameterised JDBC for grey-box assertions ("the row really is CANCELLED").
 * Values are always bound, never concatenated into SQL.
 */
public final class Jdbc {

    private final PostgresFixture db;

    Jdbc(PostgresFixture db) {
        this.db = db;
    }

    public List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public long count(String sql, Object... params) {
        Object v = query(sql, params).getFirst().values().iterator().next();
        return ((Number) v).longValue();
    }

    public int update(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = prepare(c, sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Update failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }
}
