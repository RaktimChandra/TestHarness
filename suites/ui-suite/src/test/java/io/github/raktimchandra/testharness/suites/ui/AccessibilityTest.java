package io.github.raktimchandra.testharness.suites.ui;

import static io.github.raktimchandra.testharness.ui.page.Locators.testId;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.github.raktimchandra.testharness.gate.ui.DriverPage;
import io.github.raktimchandra.testharness.gate.ui.LoginPage;
import io.github.raktimchandra.testharness.ui.a11y.A11yGate;
import io.github.raktimchandra.testharness.ui.a11y.A11yScanner;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/**
 * axe-core (WCAG 2.1 A + AA) on every page and on the states a user actually sees: errors,
 * dialogs, panels and results. Each scan is written to target/a11y/ and gated: no critical
 * or serious violations (thresholds configurable with a11y.max-*).
 */
@Tag("a11y")
@DisplayName("Accessibility (axe-core, WCAG 2.1 AA)")
class AccessibilityTest extends UiTest {

    private A11yScanner scanner(WebDriver driver) {
        String browser = Drivers.session().orElseThrow().settings().browser().id();
        return new A11yScanner(driver, browser, Path.of("target", "a11y"));
    }

    private final A11yGate gate = A11yGate.from(HarnessConfig.get());

    @Test
    void login_page_and_its_error_state(WebDriver driver) {
        LoginPage login = pages().login().open();
        scanner(driver).assertWithin("login", gate);
        login.enter("driver1", "wrong-password").submit();
        login.error();
        scanner(driver).assertWithin("login-error", gate);
    }

    @Test
    void driver_page_with_bookings_validation_and_dialog(WebDriver driver, Waits waits) {
        GateUser driverUser = fx.newDriver();
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(10, 0), 2);
        Models.Booking b = api.book(driverUser, slot.id(), GateFixtures.container());
        DriverPage page = pages().driverSignedInViaApi(driverUser);
        page.waitForStatus(b.reference(), "BOOKED");
        scanner(driver).assertWithin("driver", gate);

        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        page.enterContainer("CSQU3054384");
        page.containerHint();
        scanner(driver).assertWithin("driver-validation-error", gate);

        page.startReschedule(b.reference());
        scanner(driver).assertWithin("driver-reschedule-panel", gate);

        waits.clickable(testId("booking-cancel-" + b.reference())).click();
        waits.visible(testId("confirm-dialog"));
        scanner(driver).assertWithin("driver-cancel-dialog", gate);
    }

    @Test
    void clerk_page_with_results_and_notice(WebDriver driver) {
        Models.Slot slot = fx.slotStartingIn(Duration.ofMinutes(30), 1);
        Models.Booking b = api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        var page = pages().clerkSignedInViaApi(fx.newClerk());
        scanner(driver).assertWithin("clerk", gate);
        page.searchByReference(b.reference(), slot.start().toLocalDate());
        scanner(driver).assertWithin("clerk-results", gate);
        page.checkIn(b.reference());
        scanner(driver).assertWithin("clerk-checked-in", gate);
    }
}
