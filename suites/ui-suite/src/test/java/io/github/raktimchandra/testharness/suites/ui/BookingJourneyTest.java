package io.github.raktimchandra.testharness.suites.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.github.raktimchandra.testharness.gate.ui.DriverPage;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Booking journey")
class BookingJourneyTest extends UiTest {

    @Tag("smoke")
    @Test
    void a_driver_books_a_slot_and_sees_it_in_their_bookings() {
        GateUser driver = fx.newDriver();
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(9, 0), 2);
        DriverPage page = pages().driverSignedInViaApi(driver);
        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        assertThat(page.selectedSlotText()).contains("09:00–09:30").contains("Lane " + slot.lane());
        String reference = page.enterContainer(GateFixtures.container()).submitAndGetReference();
        page.waitForStatus(reference, "BOOKED");
        assertThat(page.slotText(reference)).contains("09:00–09:30");
        assertThat(api.booking(driver, reference).slot().id()).isEqualTo(slot.id());
    }

    @Test
    void the_slot_table_shows_the_terminal_time_and_live_remaining_places() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(14, 30), 2);
        api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        DriverPage page = pages().driverSignedInViaApi(fx.newDriver());
        page.slots().showDate(slot.start().toLocalDate());
        assertThat(page.slots().timeRange(slot.id())).isEqualTo("14:30–15:00");
        assertThat(page.slots().remaining(slot.id())).isEqualTo(1);
    }

    @Test
    void a_full_slot_cannot_be_selected() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(15, 0), 1);
        api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        DriverPage page = pages().driverSignedInViaApi(fx.newDriver());
        page.slots().showDate(slot.start().toLocalDate());
        assertThat(page.slots().selectable(slot.id())).isFalse();
    }

    @Test
    void an_invalid_check_digit_is_explained_and_blocks_submission() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(15, 30), 1);
        DriverPage page = pages().driverSignedInViaApi(fx.newDriver());
        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        page.enterContainer("CSQU3054384");
        assertThat(page.containerHint()).isEqualTo("Check digit should be 3");
        assertThat(page.containerMarkedInvalid()).isTrue();
        assertThat(page.submitEnabled()).isFalse();
    }

    @Test
    void a_remainder_ten_container_number_is_accepted_end_to_end() {
        GateUser driver = fx.newDriver();
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(16, 0), 1);
        DriverPage page = pages().driverSignedInViaApi(driver);
        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        String container = GateFixtures.remainderTenContainer();
        page.enterContainer(container);
        assertThat(page.containerMarkedInvalid()).isFalse();
        String reference = page.direction("PICK_UP").submitAndGetReference();
        assertThat(api.booking(driver, reference).containerNumber()).isEqualTo(container);
    }

    @Test
    void server_side_rejections_are_shown_to_the_driver() {
        String container = GateFixtures.container();
        api.book(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(16, 30), 1).id(), container);
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(17, 0), 1);
        DriverPage page = pages().driverSignedInViaApi(fx.newDriver());
        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        page.enterContainer(container).submit();
        assertThat(page.bookingError()).contains("already has an open appointment");
    }
}
