package io.github.raktimchandra.testharness.suites.ui;

import static io.github.raktimchandra.testharness.ui.page.Locators.testId;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.github.raktimchandra.testharness.gate.ui.DriverPage;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.visual.VisualCheck;
import io.github.raktimchandra.testharness.ui.visual.VisualSettings;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/**
 * Screenshot comparisons of stable components. Anything that legitimately changes between
 * runs (today's date, generated references, user names) is either outside the captured
 * element or masked with an ignore-region. Baselines are per platform (browser + renderer)
 * under src/test/resources/visual-baselines/.
 */
@Tag("visual")
@DisplayName("Visual regression")
class VisualRegressionTest extends UiTest {

    /**
     * The slot list above the booking form shows every slot on the chosen date, so its height
     * (and the form's sub-pixel position, which changes how borders anti-alias) depends on what
     * other tests created that day. This test therefore books on a date nothing else uses:
     * 30 days ahead, beyond the demo seeder's horizon. Found when the same form differed by
     * 2.76 % between a visual-only run and a full parallel run (CI run 38599597303).
     */
    private static final int OWN_DAY_OFFSET = 30;

    private VisualCheck visual(WebDriver driver) {
        String browser = Drivers.session().orElseThrow().settings().browser().id();
        return new VisualCheck(driver, VisualSettings.from(HarnessConfig.get(), browser));
    }

    @Test
    void login_form(WebDriver driver, Waits waits) {
        pages().login().open();
        visual(driver).matches("login-form", waits.visible(testId("login-form")));
    }

    @Test
    void login_error(WebDriver driver, Waits waits) {
        var login = pages().login().open();
        // A fresh account: failed sign-ins count towards a lock, so tests never spend a shared one's.
        login.enter(fx.newDriver().username(), "wrong-password").submit();
        login.error();
        visual(driver).matches("login-form-error", waits.visible(testId("login-form")),
                testId("login-username"));
    }

    @Test
    void booking_form_with_a_check_digit_error(WebDriver driver, Waits waits) {
        GateUser user = fx.newDriver();
        Models.Slot slot = fx.slotOn(java.time.LocalDate.now(GateFixtures.TERMINAL).plusDays(OWN_DAY_OFFSET),
                LocalTime.of(11, 0), 1);
        DriverPage page = pages().driverSignedInViaApi(user);
        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        page.enterContainer("CSQU3054384");
        page.containerHint();
        // The selected-slot line contains a date and a generated lane: masked.
        visual(driver).matches("booking-form-invalid", waits.visible(testId("book-form")),
                testId("book-selected-slot"));
    }

    @Test
    void cancel_confirmation_dialog(WebDriver driver, Waits waits) {
        GateUser user = fx.newDriver();
        Models.Booking b = api.book(user, fx.slotTomorrowAt(LocalTime.of(11, 30), 1).id(), GateFixtures.container());
        pages().driverSignedInViaApi(user);
        waits.clickable(testId("booking-cancel-" + b.reference())).click();
        // The dialog text names the reference and the date: masked.
        visual(driver).matches("cancel-dialog", waits.visible(testId("confirm-dialog")),
                org.openqa.selenium.By.id("confirm-message"));
    }

    @Test
    void clerk_search_form(WebDriver driver, Waits waits) {
        pages().clerkSignedInViaApi(fx.newClerk());
        visual(driver).matches("clerk-search-form", waits.visible(testId("search-form")), testId("search-date"));
    }
}
