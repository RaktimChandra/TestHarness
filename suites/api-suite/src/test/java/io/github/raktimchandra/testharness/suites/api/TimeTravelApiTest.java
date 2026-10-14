package io.github.raktimchandra.testharness.suites.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Test;

/**
 * Rules that can only be reached by moving the SUT's clock or flipping global switches.
 * {@code @Isolated} makes JUnit run this class alone, so no concurrent test sees the shift.
 */
@Isolated
@DisplayName("Time-dependent rules and test-support controls")
class TimeTravelApiTest extends ApiTest {

    @AfterEach
    void restoreClock() {
        fx.shiftClock(Duration.ZERO);
    }

    @Test
    void check_in_closes_thirty_minutes_after_the_slot_ends() {
        Models.Slot slot = fx.slotStartingIn(Duration.ofMinutes(10), 1);
        Models.Booking b = api.book(fx.newDriver(), slot.id(), io.github.raktimchandra.testharness.gate.api.GateFixtures.container());
        fx.shiftClock(Duration.ofMinutes(10 + 30 + 31)); // past start + 30 min slot + 30 min grace
        assertThatResponse(api.rawCheckIn(clerk(), b.reference())).hasStatus(409).hasProblemCode("CHECKIN_TOO_LATE");
    }

    @Test
    void a_slot_that_has_started_cannot_be_booked() {
        Models.Slot slot = fx.slotStartingIn(Duration.ofMinutes(5), 1);
        fx.shiftClock(Duration.ofMinutes(6));
        assertThatResponse(api.rawBook(fx.newDriver(), slot.id(),
                io.github.raktimchandra.testharness.gate.api.GateFixtures.container(), "DROP_OFF"))
                .hasStatus(400).hasProblemCode("SLOT_IN_PAST");
    }

    @Test
    void the_clock_endpoint_reports_the_offset() {
        fx.shiftClock(Duration.ofHours(2));
        Models.Clock c = api.testSupport().get("/api/test-support/clock").as(Models.Clock.class);
        assertThat(c.offsetSeconds()).isEqualTo(7200);
    }

    @Test
    void test_support_requires_the_shared_key() {
        assertThatResponse(api.anonymous().get("/api/test-support/bugs")).hasStatus(403).hasProblemCode("FORBIDDEN");
        assertThatResponse(api.anonymous().header("X-Test-Support-Key", "wrong").get("/api/test-support/bugs"))
                .hasStatus(403).hasProblemCode("FORBIDDEN");
        GateUser driver = fx.newDriver();
        // A valid user token is not a substitute for the key.
        Response withToken = api.as(driver).get("/api/test-support/bugs");
        assertThatResponse(withToken).hasStatus(403);
    }

    @Test
    void seeded_bug_switches_round_trip_and_list_every_bug() {
        List<String> before = fx.seededBugs();
        try {
            fx.setSeededBugs(List.of("OFF_BY_ONE_CAPACITY"));
            assertThat(fx.seededBugs()).containsExactly("OFF_BY_ONE_CAPACITY");
            List<Map<String, String>> available = api.testSupport().get("/api/test-support/bugs").jsonPath()
                    .getList("available");
            assertThat(available).hasSize(6);
        } finally {
            fx.setSeededBugs(before);
        }
    }
}
