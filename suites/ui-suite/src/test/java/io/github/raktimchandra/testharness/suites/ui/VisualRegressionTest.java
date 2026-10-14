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
        login.enter("driver1", "wrong-password").submit();
        login.error();
        visual(driver).matches("login-form-error", waits.visible(testId("login-form")),
                testId("login-username"));
    }

    @Test
    void booking_form_with_a_check_digit_error(WebDriver driver, Waits waits) {
        GateUser user = fx.newDriver();
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(11, 0), 1);
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
