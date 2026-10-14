package io.github.raktimchandra.gate.api.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {

    private static final String SELECT = """
            SELECT s.*,
                   (SELECT count(*) FROM booking b
                     WHERE b.slot_id = s.id AND b.status IN ('BOOKED', 'CHECKED_IN')) AS occupied
              FROM slot s
            """;

    private final JdbcClient jdbc;

    public SlotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String lane, Instant start, Instant end, int capacity) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO slot (lane, start_at, end_at, capacity) VALUES (:l, :s, :e, :c)")
                .param("l", lane).param("s", Timestamp.from(start)).param("e", Timestamp.from(end))
                .param("c", capacity)
                .update(keys, "id");
        return keys.getKeyAs(Long.class);
    }

    public boolean insertIfAbsent(String lane, Instant start, Instant end, int capacity) {
        return jdbc.sql("""
                INSERT INTO slot (lane, start_at, end_at, capacity) VALUES (:l, :s, :e, :c)
                ON CONFLICT (lane, start_at) DO NOTHING
                """)
                .param("l", lane).param("s", Timestamp.from(start)).param("e", Timestamp.from(end))
                .param("c", capacity)
                .update() == 1;
    }

    public Optional<SlotRecord> findById(long id) {
        return jdbc.sql(SELECT + " WHERE s.id = :id").param("id", id).query(SlotRepository::map).optional();
    }

    /**
     * Locks the slot row for the rest of the transaction. Every booking that changes a slot's
     * occupancy takes this lock first, which serialises the capacity check with the insert.
     */
    public boolean lockForUpdate(long id) {
        return jdbc.sql("SELECT id FROM slot WHERE id = :id FOR UPDATE").param("id", id)
                .query(Long.class).optional().isPresent();
    }

    public List<SlotRecord> findBetween(Instant from, Instant to, String lane) {
        String sql = SELECT + " WHERE s.start_at >= :from AND s.start_at < :to"
                + (lane == null ? "" : " AND s.lane = :lane")
                + " ORDER BY s.start_at, s.lane";
        JdbcClient.StatementSpec spec = jdbc.sql(sql)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to));
        if (lane != null) {
            spec = spec.param("lane", lane);
        }
        return spec.query(SlotRepository::map).list();
    }

    public boolean existsAt(String lane, Instant start) {
        return jdbc.sql("SELECT count(*) FROM slot WHERE lane = :l AND start_at = :s")
                .param("l", lane).param("s", Timestamp.from(start))
                .query(Integer.class).single() > 0;
    }

    static SlotRecord map(ResultSet rs, int row) throws SQLException {
        return new SlotRecord(rs.getLong("id"), rs.getString("lane"),
                rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
                rs.getInt("capacity"), rs.getInt("occupied"));
    }
}
