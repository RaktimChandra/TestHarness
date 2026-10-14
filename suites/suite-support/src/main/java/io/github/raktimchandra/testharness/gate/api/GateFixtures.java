package io.github.raktimchandra.testharness.gate.api;

import io.github.raktimchandra.testharness.api.auth.Credentials;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.data.TestData;
import io.github.raktimchandra.testharness.data.UniqueIds;
import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import io.github.raktimchandra.testharness.reporting.Report;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Test data that belongs to exactly one test. Each test creates its own users (via the
 * test-support API) and its own slots (in a lane nobody else uses), so tests can run in
 * parallel, in any order, against a shared SUT without seeing each other's data.
 */
public final class GateFixtures {

    /** The terminal's zone (must match the SUT's gate.terminal-zone). */
    public static final ZoneId TERMINAL = ZoneId.of(HarnessConfig.get().string("sut.terminal-zone", "Asia/Kolkata"));

    private final GateApi api;

    public GateFixtures(GateApi api) {
        this.api = api;
    }

    public static GateFixtures get() {
        return new GateFixtures(GateApi.get());
    }

    public GateApi api() {
        return api;
    }

    public GateUser newDriver() {
        return newUser("DRIVER");
    }

    public GateUser newClerk() {
        return newUser("CLERK");
    }

    /** A seeded demo user (driver1..3, clerk1..2); password from GATE_SEED_PASSWORD or the demo default. */
    public static GateUser seeded(String username) {
        String password = HarnessConfig.get().secret("GATE_SEED_PASSWORD", "gate-demo-pass").reveal();
        return new GateUser(Credentials.of(username, password), username.startsWith("clerk") ? "CLERK" : "DRIVER");
    }

    private GateUser newUser(String role) {
        Response r = api.testSupport().body(Map.of("role", role)).post("/api/test-support/users");
        if (r.statusCode() != 201) {
            throw new IllegalStateException("Could not create a " + role + ": " + r.asString());
        }
        Models.CreatedUser u = r.as(Models.CreatedUser.class);
        return new GateUser(Credentials.of(u.username(), u.password()), u.role());
    }

    /** A fresh lane name no other test uses, e.g. {@code T3K90001}. */
    public static String uniqueLane() {
        return UniqueIds.next("T", 8);
    }

    /** A slot in its own lane starting {@code lead} from now (rounded to the minute). */
    public Models.Slot slotStartingIn(Duration lead, int capacity) {
        OffsetDateTime start = OffsetDateTime.now(TERMINAL).plus(lead).truncatedTo(ChronoUnit.MINUTES);
        return api.createSlot(seeded("clerk1"), uniqueLane(), start, capacity);
    }

    /** A slot tomorrow at a fixed local terminal time, in its own lane. */
    public Models.Slot slotTomorrowAt(LocalTime time, int capacity) {
        return slotOn(LocalDate.now(TERMINAL).plusDays(1), time, capacity);
    }

    public Models.Slot slotOn(LocalDate date, LocalTime time, int capacity) {
        OffsetDateTime start = ZonedDateTime.of(date, time, TERMINAL).toOffsetDateTime();
        return api.createSlot(seeded("clerk1"), uniqueLane(), start, capacity);
    }

    public static String container() {
        return ContainerNumbers.random(TestData.random());
    }

    public static String remainderTenContainer() {
        return ContainerNumbers.withRemainderTen(TestData.random());
    }

    // ---- global switches: only for tests marked @Isolated -----------------------------

    public void setSeededBugs(Collection<String> bugs) {
        Report.note("Seeded bugs set to " + bugs);
        Response r = api.testSupport().body(Map.of("active", List.copyOf(bugs))).put("/api/test-support/bugs");
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Could not set seeded bugs: " + r.asString());
        }
    }

    public List<String> seededBugs() {
        return api.testSupport().get("/api/test-support/bugs").jsonPath().getList("active", String.class);
    }

    public void shiftClock(Duration offset) {
        Report.note("SUT clock shifted by " + offset);
        Response r = api.testSupport().body(Map.of("offsetSeconds", offset.toSeconds())).put("/api/test-support/clock");
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Could not shift clock: " + r.asString());
        }
    }
}
