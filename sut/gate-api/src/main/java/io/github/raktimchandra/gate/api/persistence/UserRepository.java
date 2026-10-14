package io.github.raktimchandra.gate.api.persistence;

import io.github.raktimchandra.gate.domain.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findByUsername(String username) {
        return jdbc.sql("SELECT * FROM app_user WHERE username = :u")
                .param("u", username)
                .query(UserRepository::map)
                .optional();
    }

    public Optional<UserRecord> findById(long id) {
        return jdbc.sql("SELECT * FROM app_user WHERE id = :id").param("id", id).query(UserRepository::map).optional();
    }

    /** Inserts the user unless the username exists. Returns true when a row was created. */
    public boolean insertIfAbsent(String username, String passwordHash, Role role, String displayName, String plate) {
        return jdbc.sql("""
                INSERT INTO app_user (username, password_hash, role, display_name, truck_plate)
                VALUES (:u, :p, :r, :d, :t) ON CONFLICT (username) DO NOTHING
                """)
                .param("u", username).param("p", passwordHash).param("r", role.name())
                .param("d", displayName).param("t", plate)
                .update() == 1;
    }

    static UserRecord map(ResultSet rs, int row) throws SQLException {
        return new UserRecord(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")), rs.getString("display_name"), rs.getString("truck_plate"));
    }
}
