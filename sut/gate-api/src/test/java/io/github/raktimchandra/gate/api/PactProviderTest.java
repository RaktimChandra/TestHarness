package io.github.raktimchandra.gate.api;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.github.raktimchandra.gate.api.config.ShiftableClock;
import io.github.raktimchandra.gate.api.persistence.UserRepository;
import io.github.raktimchandra.gate.api.security.TokenService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the consumer contract generated from the UI's API client
 * ({@code contracts/pacts/gate-ui-gate-api.json}) against the real provider.
 *
 * <p>Provider states create exactly the data each interaction needs (fixed ids 9001-9004,
 * references GA-PACT0001/2) and pin the business clock to 2026-11-20 09:00 terminal time,
 * so the contract's dates stay "in the future" no matter when the verification runs. The
 * consumer sends placeholder bearer tokens; they are swapped for real tokens of the user
 * the state names.
 */
@Provider("gate-api")
@PactFolder("../../contracts/pacts")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "gate.seed.password=gate-demo-pass", "gate.terminal-zone=Asia/Kolkata"})
class PactProviderTest {

    private static final Instant PINNED_NOW = OffsetDateTime.parse("2026-11-20T09:00:00+05:30").toInstant();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TokenService tokens;

    @Autowired
    UserRepository users;

    @Autowired
    ShiftableClock clock;

    private String bearer;

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @AfterEach
    void unpinClock() {
        clock.setOffset(Duration.ZERO);
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void interactionHonoursTheContract(PactVerificationContext context, HttpRequest request) {
        if (request.containsHeader("Authorization") && bearer != null) {
            request.setHeader("Authorization", bearer);
        }
        context.verifyInteraction();
    }

    @State("the demo users exist")
    void demoUsers() {
        reset();
        bearer = null;
    }

    @State("slot 9001 exists with room")
    void slotWithRoom() {
        reset();
        slot(9001, "2026-12-01T10:00:00+05:30", 4);
        bearer = tokenFor("driver1");
    }

    @State("slot 9003 is full")
    void fullSlot() {
        reset();
        slot(9003, "2026-12-01T11:00:00+05:30", 1);
        booking("GA-PACT0003", "driver2", 9003, "TGHU1234567");
        bearer = tokenFor("driver1");
    }

    @State("driver1 has booking GA-PACT0001 in slot 9001")
    void existingBooking() {
        reset();
        slot(9001, "2026-12-01T10:00:00+05:30", 4);
        slot(9002, "2026-12-01T10:30:00+05:30", 4);
        booking("GA-PACT0001", "driver1", 9001, "CSQU3054383");
        bearer = tokenFor("driver1");
    }

    @State("booking GA-PACT0002 is inside its check-in window")
    void checkInWindow() {
        reset();
        slot(9004, "2026-11-20T10:00:00+05:30", 2);
        booking("GA-PACT0002", "driver1", 9004, "MSCU1000070");
        bearer = tokenFor("clerk1");
    }

    private void reset() {
        clock.setOffset(Duration.between(Instant.now(), PINNED_NOW));
        jdbc.sql("DELETE FROM booking WHERE slot_id BETWEEN 9001 AND 9004 OR reference LIKE 'GA-PACT%'").update();
        jdbc.sql("DELETE FROM slot WHERE id BETWEEN 9001 AND 9004").update();
    }

    private void slot(long id, String start, int capacity) {
        Instant s = OffsetDateTime.parse(start).toInstant();
        jdbc.sql("INSERT INTO slot (id, lane, start_at, end_at, capacity) VALUES (:id, 'PACT', :s, :e, :c)")
                .param("id", id).param("s", Timestamp.from(s)).param("e", Timestamp.from(s.plus(Duration.ofMinutes(30))))
                .param("c", capacity).update();
    }

    private void booking(String reference, String driver, long slotId, String container) {
        long driverId = users.findByUsername(driver).orElseThrow().id();
        jdbc.sql("""
                INSERT INTO booking (reference, driver_id, slot_id, container_number, direction, status, created_at, updated_at)
                VALUES (:r, :d, :s, :c, 'DROP_OFF', 'BOOKED', :now, :now)
                """)
                .param("r", reference).param("d", driverId).param("s", slotId).param("c", container)
                .param("now", Timestamp.from(PINNED_NOW)).update();
    }

    private String tokenFor(String username) {
        return "Bearer " + tokens.issue(users.findByUsername(username).orElseThrow()).value();
    }
}
