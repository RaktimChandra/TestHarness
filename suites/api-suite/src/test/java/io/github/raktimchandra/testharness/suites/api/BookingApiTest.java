package io.github.raktimchandra.testharness.suites.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.data.source.JsonCases;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

@DisplayName("Booking API")
class BookingApiTest extends ApiTest {

    record ContainerCase(String name, String value, int status, String code) {
        @Override
        public String toString() {
            return name;
        }
    }

    @Tag("smoke")
    @Test
    void a_driver_books_a_slot() {
        GateUser driver = fx.newDriver();
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(10, 0), 2);
        String container = GateFixtures.container();
        Response r = api.rawBook(driver, slot.id(), container, "PICK_UP");
        assertThatResponse(r).hasStatus(201);
        Models.Booking b = r.as(Models.Booking.class);
        assertThat(r.header("Location")).isEqualTo("/api/bookings/" + b.reference());
        assertThat(b.status()).isEqualTo("BOOKED");
        assertThat(b.direction()).isEqualTo("PICK_UP");
        assertThat(b.containerNumber()).isEqualTo(container);
        assertThat(b.driver().username()).isEqualTo(driver.username());
        assertThat(b.slot().id()).isEqualTo(slot.id());
        assertThat(b.checkedInAt()).isNull();
    }

    @Test
    void the_booking_reports_the_slot_time_exactly_as_the_slot_does() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(10, 30), 1);
        Models.Booking b = api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        assertThat(b.slot().start()).isEqualTo(slot.start());
        assertThat(b.slot().start().toLocalTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @ParameterizedTest(name = "{0}")
    @JsonCases(value = "/data/container-cases.json", type = ContainerCase.class)
    void container_number_rules_are_enforced_by_the_api(ContainerCase c) {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(11, 0), 2);
        GateUser driver = fx.newDriver();
        Response r = api.rawBook(driver, slot.id(), c.value(), "DROP_OFF");
        assertThatResponse(r).hasStatus(c.status());
        if (c.code() != null) {
            assertThatResponse(r).hasProblemCode(c.code());
        } else {
            Models.Booking b = r.as(Models.Booking.class);
            assertThat(b.containerNumber()).isEqualTo(c.value().strip().toUpperCase());
            // These numbers are fixed, not generated: release them so a re-run against a
            // persistent database (Docker mode) is not blocked by "one open booking per container".
            assertThatResponse(api.rawCancel(driver, b.reference())).hasStatus(200);
        }
    }

    @Test
    void a_generated_remainder_ten_container_is_accepted() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(11, 30), 1);
        String edge = GateFixtures.remainderTenContainer();
        assertThatResponse(api.rawBook(fx.newDriver(), slot.id(), edge, "DROP_OFF")).hasStatus(201);
    }

    @Test
    void capacity_is_enforced_at_the_boundary() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(12, 0), 2);
        api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        Response third = api.rawBook(fx.newDriver(), slot.id(), GateFixtures.container(), "DROP_OFF");
        assertThatResponse(third).hasStatus(409).hasProblemCode("SLOT_FULL");
        assertThat(api.slot(clerk(), slot.id()).booked()).isEqualTo(2);
    }

    @Test
    void concurrent_requests_for_the_last_place_admit_exactly_one() throws Exception {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(12, 30), 1);
        int drivers = 6;
        List<GateUser> users = new ArrayList<>();
        for (int i = 0; i < drivers; i++) {
            GateUser u = fx.newDriver();
            api.as(u).get("/api/auth/me"); // warm the token cache so requests start together
            users.add(u);
        }
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(drivers);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (GateUser u : users) {
                results.add(pool.submit(() -> {
                    go.await();
                    return api.rawBook(u, slot.id(), GateFixtures.container(), "DROP_OFF").statusCode();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(drivers - 1);
            assertThat(api.slot(clerk(), slot.id()).booked()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void a_container_can_have_only_one_open_appointment() {
        String container = GateFixtures.container();
        api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(13, 0), 2).id(), container);
        Response second = api.rawBook(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(13, 30), 2).id(), container,
                "DROP_OFF");
        assertThatResponse(second).hasStatus(409).hasProblemCode("DUPLICATE_ACTIVE_BOOKING");
    }

    @Test
    void a_container_can_be_booked_again_after_cancelling() {
        GateUser driver = fx.newDriver();
        String container = GateFixtures.container();
        Models.Booking first = api.book(driver, fx.slotTomorrowAt(LocalTime.of(14, 0), 1).id(), container);
        assertThatResponse(api.rawCancel(driver, first.reference())).hasStatus(200);
        assertThatResponse(api.rawBook(driver, fx.slotTomorrowAt(LocalTime.of(14, 30), 1).id(), container, "DROP_OFF"))
                .hasStatus(201);
    }

    @Test
    void required_fields_and_enum_values_are_validated() {
        Response empty = api.as(fx.newDriver()).body(Map.of()).post("/api/bookings");
        assertThatResponse(empty).hasStatus(400).hasProblemCode("VALIDATION_FAILED");
        assertThat(empty.jsonPath().getList("errors.field", String.class))
                .containsExactly("containerNumber", "direction", "slotId");
        Map<String, Object> badDirection = new HashMap<>(Map.of("slotId", 1, "containerNumber", "CSQU3054383"));
        badDirection.put("direction", "SIDEWAYS");
        assertThatResponse(api.as(fx.newDriver()).body(badDirection).post("/api/bookings"))
                .hasStatus(400).hasProblemCode("VALIDATION_FAILED");
    }

    @Test
    void an_unknown_slot_is_404() {
        assertThatResponse(api.rawBook(fx.newDriver(), 999_999_999L, GateFixtures.container(), "DROP_OFF"))
                .hasStatus(404).hasProblemCode("NOT_FOUND");
    }

    @Test
    void clerks_cannot_book_and_anonymous_callers_must_authenticate() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(15, 0), 1);
        assertThatResponse(api.rawBook(clerk(), slot.id(), GateFixtures.container(), "DROP_OFF"))
                .hasStatus(403).hasProblemCode("FORBIDDEN");
        assertThatResponse(api.anonymous().body(Map.of("slotId", slot.id(), "containerNumber", "CSQU3054383",
                "direction", "DROP_OFF")).post("/api/bookings")).hasStatus(401);
    }

    @Test
    void references_are_case_insensitive_on_lookup() {
        GateUser driver = fx.newDriver();
        Models.Booking b = api.book(driver, fx.slotTomorrowAt(LocalTime.of(15, 30), 1).id(), GateFixtures.container());
        assertThatResponse(api.rawGetBooking(driver, b.reference().toLowerCase())).hasStatus(200)
                .hasJsonValue("reference", b.reference());
    }
}
