package io.github.raktimchandra.testharness.suites.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.github.raktimchandra.testharness.gate.ui.ClerkPage;
import io.github.raktimchandra.testharness.gate.ui.DriverPage;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Reschedule, cancel and check-in journeys")
class ManageBookingJourneyTest extends UiTest {

    @Tag("smoke")
    @Test
    void a_driver_reschedules_to_another_slot() {
        GateUser driver = fx.newDriver();
        Models.Slot from = fx.slotTomorrowAt(LocalTime.of(10, 0), 1);
        Models.Slot to = fx.slotTomorrowAt(LocalTime.of(11, 30), 1);
        Models.Booking b = api.book(driver, from.id(), GateFixtures.container());
        DriverPage page = pages().driverSignedInViaApi(driver).startReschedule(b.reference());
        page.rescheduleSlots().showDate(to.start().toLocalDate());
        assertThat(page.rescheduleSlots().listed(from.id())).as("current slot is not offered").isFalse();
        page.rescheduleSlots().select(to.id());
        page.confirmReschedule();
        page.waitForStatus(b.reference(), "BOOKED");
        assertThat(waitForSlotText(page, b.reference(), "11:30–12:00")).isTrue();
        assertThat(api.booking(driver, b.reference()).slot().id()).isEqualTo(to.id());
    }

    @Tag("smoke")
    @Test
    void cancelling_asks_for_confirmation_and_keep_it_changes_nothing() {
        GateUser driver = fx.newDriver();
        Models.Booking b = api.book(driver, fx.slotTomorrowAt(LocalTime.of(12, 0), 1).id(), GateFixtures.container());
        DriverPage page = pages().driverSignedInViaApi(driver);
        page.cancel(b.reference(), false);
        assertThat(page.status(b.reference())).isEqualTo("BOOKED");
        page.cancel(b.reference(), true);
        page.waitForStatus(b.reference(), "CANCELLED");
        assertThat(page.hasActions(b.reference())).isFalse();
        assertThat(api.booking(driver, b.reference()).status()).isEqualTo("CANCELLED");
    }

    @Tag("smoke")
    @Test
    void a_clerk_finds_a_booking_and_checks_the_truck_in() {
        Models.Slot slot = fx.slotStartingIn(Duration.ofMinutes(30), 1);
        Models.Booking b = api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        ClerkPage page = pages().clerkSignedInViaApi(fx.newClerk());
        page.searchByReference(b.reference(), slot.start().toLocalDate());
        assertThat(page.status(b.reference())).isEqualTo("BOOKED");
        page.checkIn(b.reference());
        assertThat(page.notice()).isEqualTo(b.reference() + " checked in.");
        assertThat(page.canCheckIn(b.reference())).isFalse();
    }

    @Test
    void a_too_early_check_in_shows_the_reason() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(13, 0), 1);
        Models.Booking b = api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        ClerkPage page = pages().clerkSignedInViaApi(fx.newClerk());
        page.searchByReference(b.reference(), slot.start().toLocalDate()).clickCheckIn(b.reference());
        assertThat(page.error()).contains("Check-in opens 120 minutes before the slot");
        assertThat(page.status(b.reference())).isEqualTo("BOOKED");
    }

    /** The table re-renders after the move; wait for the new time rather than reading once. */
    private static boolean waitForSlotText(DriverPage page, String reference, String expected) {
        var s = Drivers.session().orElseThrow();
        return new Waits(s.driver(), s.settings().explicitWait(), s.settings().pollInterval())
                .holdsWithin("booking " + reference + " to show " + expected,
                        d -> page.slotText(reference).contains(expected));
    }
}
