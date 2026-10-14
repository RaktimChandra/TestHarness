package io.github.raktimchandra.gate.api.persistence;

import io.github.raktimchandra.gate.domain.BookingDirection;
import io.github.raktimchandra.gate.domain.BookingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private static final String SELECT = """
            SELECT b.*, u.username AS driver_username, u.display_name AS driver_display_name,
                   s.lane, s.start_at AS slot_start, s.end_at AS slot_end
              FROM booking b
              JOIN app_user u ON u.id = b.driver_id
              JOIN slot s ON s.id = b.slot_id
            """;

    private final JdbcClient jdbc;

    public BookingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int countOccupying(long slotId) {
        return jdbc.sql("SELECT count(*) FROM booking WHERE slot_id = :s AND status IN ('BOOKED', 'CHECKED_IN')")
                .param("s", slotId).query(Integer.class).single();
    }

    public void insert(String reference, long driverId, long slotId, String container, BookingDirection direction,
                       Instant now) {
        jdbc.sql("""
                INSERT INTO booking (reference, driver_id, slot_id, container_number, direction, status,
                                     created_at, updated_at)
                VALUES (:r, :d, :s, :c, :dir, 'BOOKED', :now, :now)
                """)
                .param("r", reference).param("d", driverId).param("s", slotId).param("c", container)
                .param("dir", direction.name()).param("now", Timestamp.from(now))
                .update();
    }

    public Optional<BookingRecord> findByReference(String reference) {
        return jdbc.sql(SELECT + " WHERE b.reference = :r").param("r", reference)
                .query(BookingRepository::map).optional();
    }

    public void updateSlot(long bookingId, long slotId, Instant now) {
        jdbc.sql("UPDATE booking SET slot_id = :s, updated_at = :now WHERE id = :id")
                .param("s", slotId).param("now", Timestamp.from(now)).param("id", bookingId).update();
    }

    public void updateStatus(long bookingId, BookingStatus status, Instant now) {
        jdbc.sql("UPDATE booking SET status = :st, updated_at = :now WHERE id = :id")
                .param("st", status.name()).param("now", Timestamp.from(now)).param("id", bookingId).update();
    }

    public void markCheckedIn(long bookingId, long clerkId, Instant now) {
        jdbc.sql("""
                UPDATE booking SET status = 'CHECKED_IN', checked_in_at = :now, checked_in_by = :c, updated_at = :now
                 WHERE id = :id
                """)
                .param("now", Timestamp.from(now)).param("c", clerkId).param("id", bookingId).update();
    }

    /** Search with optional filters; every filter is bound as a parameter (never concatenated). */
    public List<BookingRecord> search(BookingSearch q) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (q.driverId() != null) {
            where.add("b.driver_id = :driverId");
            params.put("driverId", q.driverId());
        }
        if (q.containerNumber() != null) {
            where.add("b.container_number = :container");
            params.put("container", q.containerNumber());
        }
        if (q.reference() != null) {
            where.add("b.reference = :reference");
            params.put("reference", q.reference());
        }
        if (q.status() != null) {
            where.add("b.status = :status");
            params.put("status", q.status().name());
        }
        if (q.driverUsername() != null) {
            where.add("u.username = :driverUsername");
            params.put("driverUsername", q.driverUsername());
        }
        if (q.from() != null) {
            where.add("s.start_at >= :from AND s.start_at < :to");
            params.put("from", Timestamp.from(q.from()));
            params.put("to", Timestamp.from(q.to()));
        }
        String sql = SELECT + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY s.start_at, b.reference LIMIT :limit";
        params.put("limit", q.limit());
        return jdbc.sql(sql).params(params).query(BookingRepository::map).list();
    }

    static BookingRecord map(ResultSet rs, int row) throws SQLException {
        Timestamp checkedIn = rs.getTimestamp("checked_in_at");
        return new BookingRecord(rs.getLong("id"), rs.getString("reference"), rs.getLong("driver_id"),
                rs.getString("driver_username"), rs.getString("driver_display_name"), rs.getLong("slot_id"),
                rs.getString("lane"), rs.getTimestamp("slot_start").toInstant(),
                rs.getTimestamp("slot_end").toInstant(), rs.getString("container_number"),
                BookingDirection.valueOf(rs.getString("direction")), BookingStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                checkedIn == null ? null : checkedIn.toInstant());
    }

    /** Search criteria; {@code from}/{@code to} bound the slot start. */
    public record BookingSearch(Long driverId, String containerNumber, String reference, BookingStatus status,
                                String driverUsername, Instant from, Instant to, int limit) {}
}
