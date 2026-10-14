package io.github.raktimchandra.testharness.suites.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Reschedule, cancel and check-in: the state machine BOOKED -> {CANCELLED | CHECKED_IN}. */
@DisplayName("Booking lifecycle API")
class LifecycleApiTest extends ApiTest {

    @Nested
    @DisplayName("reschedule")
    class Reschedule {

        @Tag("smoke")
        @Test
        void moves_the_booking_and_both_slots_reflect_it() {
            GateUser driver = fx.newDriver();
            Models.Slot from = fx.slotTomorrowAt(LocalTime.of(9, 0), 2);
            Models.Slot to = fx.slotTomorrowAt(LocalTime.of(9, 30), 2);
            Models.Booking b = api.book(driver, from.id(), GateFixtures.container());
            Response r = api.rawReschedule(driver, b.reference(), to.id());
            assertThatResponse(r).hasStatus(200);
            assertThat(r.as(Models.Booking.class).slot().id()).isEqualTo(to.id());
            assertThat(api.slot(driver, from.id()).booked()).isZero();
            assertThat(api.slot(driver, to.id()).booked()).isEqualTo(1);
        }

        @Test
        void to_the_same_slot_is_rejected() {
            GateUser driver = fx.newDriver();
            Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(10, 0), 2);
            Models.Booking b = api.book(driver, slot.id(), GateFixtures.container());
            assertThatResponse(api.rawReschedule(driver, b.reference(), slot.id())).hasStatus(409)
                    .hasProblemCode("SAME_SLOT");
        }

        @Test
        void to_a_full_slot_is_rejected_and_nothing_changes() {
            GateUser driver = fx.newDriver();
            Models.Slot full = fx.slotTomorrowAt(LocalTime.of(10, 30), 1);
            api.book(fx.newDriver(), full.id(), GateFixtures.container());
            Models.Slot mine = fx.slotTomorrowAt(LocalTime.of(11, 0), 1);
            Models.Booking b = api.book(driver, mine.id(), GateFixtures.container());
            assertThatResponse(api.rawReschedule(driver, b.reference(), full.id())).hasStatus(409)
                    .hasProblemCode("SLOT_FULL");
            assertThat(api.booking(driver, b.reference()).slot().id()).isEqualTo(mine.id());
        }

        @Test
        void to_an_unknown_slot_is_404() {
            GateUser driver = fx.newDriver();
            Models.Booking b = api.book(driver, fx.slotTomorrowAt(LocalTime.of(11, 30), 1).id(), GateFixtures.container());
            assertThatResponse(api.rawReschedule(driver, b.reference(), 999_999_999L)).hasStatus(404)
                    .hasProblemCode("NOT_FOUND");
        }

        @Test
        void inside_the_one_hour_cutoff_is_rejected() {
            GateUser driver = fx.newDriver();
            Models.Booking b = api.book(driver, fx.slotStartingIn(Duration.ofMinutes(30), 1).id(), GateFixtures.container());
            assertThatResponse(api.rawReschedule(driver, b.reference(), fx.slotTomorrowAt(LocalTime.of(12, 0), 1).id()))
                    .hasStatus(409).hasProblemCode("CHANGE_CUTOFF_PASSED");
        }

        @Test
        void a_cancelled_booking_cannot_be_rescheduled() {
            GateUser driver = fx.newDriver();
            Models.Booking b = api.book(driver, fx.slotTomorrowAt(LocalTime.of(12, 30), 1).id(), GateFixtures.container());
            api.rawCancel(driver, b.reference());
            assertThatResponse(api.rawReschedule(driver, b.reference(), fx.slotTomorrowAt(LocalTime.of(13, 0), 1).id()))
                    .hasStatus(409).hasProblemCode("INVALID_STATUS_TRANSITION");
        }

        @Test
        void another_driver_cannot_reschedule_it() {
            Models.Booking b = api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(13, 30), 1).id(),
                    GateFixtures.container());
            Models.Slot target = fx.slotTomorrowAt(LocalTime.of(14, 0), 1);
            assertThatResponse(api.rawReschedule(fx.newDriver(), b.reference(), target.id())).hasStatus(404)
                    .hasProblemCode("NOT_FOUND");
            assertThat(api.slot(clerk(), target.id()).booked()).isZero();
        }

        @Test
        void clerks_cannot_reschedule_for_drivers() {
            Models.Booking b = api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(14, 30), 1).id(),
                    GateFixtures.container());
            assertThatResponse(api.rawReschedule(clerk(), b.reference(), fx.slotTomorrowAt(LocalTime.of(15, 0), 1).id()))
                    .hasStatus(403).hasProblemCode("FORBIDDEN");
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Tag("smoke")
        @Test
        void cancelling_frees_the_place() {
            GateUser driver = fx.newDriver();
            Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(15, 30), 1);
            Models.Booking b = api.book(driver, slot.id(), GateFixtures.container());
            Response r = api.rawCancel(driver, b.reference());
            assertThatResponse(r).hasStatus(200).hasJsonValue("status", "CANCELLED");
            assertThat(api.slot(driver, slot.id()).remaining()).isEqualTo(1);
            assertThatResponse(api.rawBook(fx.newDriver(), slot.id(), GateFixtures.container(), "DROP_OFF")).hasStatus(201);
        }

        @Test
        void cancelling_twice_is_an_invalid_transition() {
            GateUser driver = fx.newDriver();
            Models.Booking b = api.book(driver, fx.slotTomorrowAt(LocalTime.of(16, 0), 1).id(), GateFixtures.container());
            api.rawCancel(driver, b.reference());
            assertThatResponse(api.rawCancel(driver, b.reference())).hasStatus(409)
                    .hasProblemCode("INVALID_STATUS_TRANSITION");
        }

        @Test
        void another_driver_cannot_cancel_it() {
            GateUser owner = fx.newDriver();
            Models.Booking b = api.book(owner, fx.slotTomorrowAt(LocalTime.of(16, 30), 1).id(), GateFixtures.container());
            assertThatResponse(api.rawCancel(fx.newDriver(), b.reference())).hasStatus(404).hasProblemCode("NOT_FOUND");
            assertThat(api.booking(owner, b.reference()).status()).isEqualTo("BOOKED");
        }

        @Test
        void inside_the_cutoff_it_cannot_be_cancelled() {
            GateUser driver = fx.newDriver();
            Models.Booking b = api.book(driver, fx.slotStartingIn(Duration.ofMinutes(40), 1).id(), GateFixtures.container());
            assertThatResponse(api.rawCancel(driver, b.reference())).hasStatus(409).hasProblemCode("CHANGE_CUTOFF_PASSED");
        }

        @Test
        void clerks_cannot_cancel_and_unknown_references_are_404() {
            Models.Booking b = api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(17, 0), 1).id(),
                    GateFixtures.container());
            assertThatResponse(api.rawCancel(clerk(), b.reference())).hasStatus(403).hasProblemCode("FORBIDDEN");
            assertThatResponse(api.rawCancel(fx.newDriver(), "GA-00000000")).hasStatus(404).hasProblemCode("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("check-in")
    class CheckIn {

        @Tag("smoke")
        @Test
        void a_clerk_checks_a_truck_in_within_the_window() {
            Models.Booking b = api.book(fx.newDriver(), fx.slotStartingIn(Duration.ofMinutes(30), 1).id(),
                    GateFixtures.container());
            Response r = api.rawCheckIn(fx.newClerk(), b.reference());
            assertThatResponse(r).hasStatus(200).hasJsonValue("status", "CHECKED_IN");
            assertThat(r.as(Models.Booking.class).checkedInAt()).isNotNull();
        }

        @Test
        void too_early_is_rejected() {
            Models.Booking b = api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(18, 0), 1).id(),
                    GateFixtures.container());
            assertThatResponse(api.rawCheckIn(clerk(), b.reference())).hasStatus(409).hasProblemCode("CHECKIN_TOO_EARLY");
        }

        @Test
        void checking_in_twice_or_after_cancelling_is_invalid() {
            GateUser driver = fx.newDriver();
            Models.Booking done = api.book(driver, fx.slotStartingIn(Duration.ofMinutes(35), 2).id(),
                    GateFixtures.container());
            api.rawCheckIn(clerk(), done.reference());
            assertThatResponse(api.rawCheckIn(clerk(), done.reference())).hasStatus(409)
                    .hasProblemCode("INVALID_STATUS_TRANSITION");
            Models.Booking cancelled = api.book(driver, fx.slotTomorrowAt(LocalTime.of(18, 30), 1).id(),
                    GateFixtures.container());
            api.rawCancel(driver, cancelled.reference());
            assertThatResponse(api.rawCheckIn(clerk(), cancelled.reference())).hasStatus(409)
                    .hasProblemCode("INVALID_STATUS_TRANSITION");
        }

        @Test
        void drivers_cannot_check_themselves_in() {
            GateUser driver = fx.newDriver();
            Models.Booking b = api.book(driver, fx.slotStartingIn(Duration.ofMinutes(30), 1).id(), GateFixtures.container());
            assertThatResponse(api.rawCheckIn(driver, b.reference())).hasStatus(403).hasProblemCode("FORBIDDEN");
        }

        @Test
        void unknown_reference_is_404() {
            assertThatResponse(api.rawCheckIn(clerk(), "GA-ZZZZZZZZ")).hasStatus(404).hasProblemCode("NOT_FOUND");
        }
    }
}
