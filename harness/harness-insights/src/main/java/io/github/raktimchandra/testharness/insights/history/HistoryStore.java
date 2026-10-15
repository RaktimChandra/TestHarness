package io.github.raktimchandra.testharness.insights.history;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Test history in PostgreSQL. Inserts are idempotent (a run re-ingested from its JSON Lines
 * file is not counted twice), so CI can rebuild the database from archived files at any time.
 */
public final class HistoryStore implements AutoCloseable {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS test_result (
                id              BIGSERIAL PRIMARY KEY,
                run_id          TEXT        NOT NULL,
                commit_sha      TEXT        NOT NULL,
                branch          TEXT,
                suite           TEXT        NOT NULL,
                test_key        TEXT        NOT NULL,
                display_name    TEXT        NOT NULL,
                status          TEXT        NOT NULL CHECK (status IN ('PASSED', 'FAILED', 'SKIPPED')),
                duration_ms     BIGINT      NOT NULL,
                failure_cause   TEXT,
                failure_type    TEXT,
                failure_message TEXT,
                environment     TEXT,
                started_at      TIMESTAMPTZ NOT NULL,
                UNIQUE (run_id, suite, test_key, display_name)
            );
            CREATE INDEX IF NOT EXISTS test_result_case ON test_result (test_key, commit_sha, started_at);
            CREATE INDEX IF NOT EXISTS test_result_run ON test_result (run_id);
            """;

    private final Connection connection;

    private HistoryStore(Connection connection) {
        this.connection = connection;
    }

    public static HistoryStore open(String jdbcUrl, String user, String password) {
        try {
            Connection c = DriverManager.getConnection(jdbcUrl, user, password);
            try (Statement s = c.createStatement()) {
                s.execute(SCHEMA);
            }
            return new HistoryStore(c);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open test history at " + jdbcUrl + ": " + e.getMessage(), e);
        }
    }

    /** Inserts results; returns how many were new. */
    public int insert(List<TestResult> results) {
        String sql = """
                INSERT INTO test_result (run_id, commit_sha, branch, suite, test_key, display_name, status, duration_ms,
                                         failure_cause, failure_type, failure_message, environment, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (run_id, suite, test_key, display_name) DO NOTHING
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (TestResult r : results) {
                ps.setString(1, r.runId());
                ps.setString(2, r.commitSha());
                ps.setString(3, r.branch());
                ps.setString(4, r.suite());
                ps.setString(5, r.key());
                ps.setString(6, r.displayName());
                ps.setString(7, r.status().name());
                ps.setLong(8, r.durationMs());
                ps.setString(9, r.failureCause());
                ps.setString(10, r.failureType());
                ps.setString(11, r.failureMessage());
                ps.setString(12, r.environment());
                ps.setTimestamp(13, Timestamp.from(r.startedAt()));
                ps.addBatch();
            }
            int inserted = 0;
            for (int n : ps.executeBatch()) {
                inserted += Math.max(n, 0);
            }
            return inserted;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot write test history: " + e.getMessage(), e);
        }
    }

    /** Every stored result, oldest first (history tables here stay small: thousands of rows). */
    public List<TestResult> all() {
        return query("SELECT * FROM test_result ORDER BY started_at, id");
    }

    public List<TestResult> forKey(String key) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM test_result WHERE test_key = ? ORDER BY started_at, id")) {
            ps.setString(1, key);
            return read(ps.executeQuery());
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private List<TestResult> query(String sql) {
        try (Statement s = connection.createStatement()) {
            return read(s.executeQuery(sql));
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static List<TestResult> read(ResultSet rs) throws SQLException {
        List<TestResult> out = new ArrayList<>();
        try (rs) {
            while (rs.next()) {
                out.add(new TestResult(rs.getString("run_id"), rs.getString("commit_sha"), rs.getString("branch"),
                        rs.getString("suite"), rs.getString("test_key"), rs.getString("display_name"),
                        TestResult.Status.valueOf(rs.getString("status")), rs.getLong("duration_ms"),
                        rs.getString("failure_cause"), rs.getString("failure_type"), rs.getString("failure_message"),
                        rs.getString("environment"), rs.getTimestamp("started_at").toInstant()));
            }
        }
        return out;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // closing a connection that already failed
        }
    }
}
