package io.github.raktimchandra.testharness.suites.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Search and object-level access")
class SearchAndAccessApiTest extends ApiTest {

    @Tag("smoke")
    @Test
    void a_driver_lists_only_their_own_bookings() {
        GateUser me = fx.newDriver();
        GateUser other = fx.newDriver();
        Models.Booking mine = api.book(me, fx.slotTomorrowAt(LocalTime.of(8, 0), 2).id(), GateFixtures.container());
        Models.Booking theirs = api.book(other, fx.slotTomorrowAt(LocalTime.of(8, 30), 2).id(), GateFixtures.container());
        List<Models.Booking> list = api.search(me, Map.of());
        assertThat(list).extracting(Models.Booking::reference).contains(mine.reference())
                .doesNotContain(theirs.reference());
        // A driver's filter on someone else's reference still returns nothing.
        assertThat(api.search(me, Map.of("reference", theirs.reference()))).isEmpty();
    }

    @Test
    void a_driver_cannot_read_another_drivers_booking() {
        Models.Booking theirs = api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(9, 0), 1).id(),
                GateFixtures.container());
        assertThatResponse(api.rawGetBooking(fx.newDriver(), theirs.reference())).hasStatus(404)
                .hasProblemCode("NOT_FOUND");
    }

    @Test
    void a_clerk_can_read_any_booking() {
        Models.Booking b = api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(9, 30), 1).id(), GateFixtures.container());
        assertThat(api.booking(fx.newClerk(), b.reference()).reference()).isEqualTo(b.reference());
    }

    @Test
    void clerk_filters_combine() {
        GateUser driver = fx.newDriver();
        String container = GateFixtures.container();
        Models.Booking b = api.book(driver, fx.slotTomorrowAt(LocalTime.of(10, 0), 1).id(), container);
        LocalDate tomorrow = LocalDate.now(GateFixtures.TERMINAL).plusDays(1);
        assertThat(api.search(clerk(), Map.of("containerNumber", container.toLowerCase())))
                .extracting(Models.Booking::reference).containsExactly(b.reference());
        assertThat(api.search(clerk(), Map.of("reference", b.reference().toLowerCase(), "date", tomorrow.toString(),
                "status", "BOOKED", "driver", driver.username()))).hasSize(1);
        assertThat(api.search(clerk(), Map.of("reference", b.reference(), "status", "CANCELLED"))).isEmpty();
        assertThat(api.search(clerk(), Map.of("reference", b.reference(), "date", tomorrow.plusDays(1).toString())))
                .isEmpty();
    }

    @Test
    void invalid_filter_values_are_400() {
        assertThatResponse(api.rawSearch(clerk(), Map.of("status", "LOST"))).hasStatus(400)
                .hasProblemCode("VALIDATION_FAILED");
        assertThatResponse(api.rawSearch(clerk(), Map.of("date", "tomorrow"))).hasStatus(400)
                .hasProblemCode("VALIDATION_FAILED");
    }

    @Test
    void injection_payloads_in_filters_are_treated_as_plain_values() {
        assertThat(api.search(clerk(), Map.of("containerNumber", "' OR '1'='1"))).isEmpty();
        assertThat(api.search(clerk(), Map.of("driver", "x'; DROP TABLE booking; --"))).isEmpty();
        // The table is still there and working.
        Models.Booking b = api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(10, 30), 1).id(), GateFixtures.container());
        assertThat(api.search(clerk(), Map.of("reference", b.reference()))).hasSize(1);
    }
}
