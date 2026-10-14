package io.github.raktimchandra.testharness.suites.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.ui.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

@DisplayName("Login journey")
class LoginJourneyTest extends UiTest {

    @Tag("smoke")
    @Test
    void a_driver_signs_in_and_lands_on_the_driver_page() {
        GateUser driver = fx.newDriver();
        var page = pages().login().open().loginAsDriver(driver);
        assertThat(page.currentPath()).isEqualTo("/driver");
    }

    @Test
    void a_clerk_signs_in_and_lands_on_the_clerk_page() {
        var page = pages().login().open().loginAsClerk(fx.newClerk());
        assertThat(page.currentPath()).isEqualTo("/clerk");
    }

    @Test
    void a_wrong_password_shows_an_error_and_stays_on_login() {
        LoginPage login = pages().login().open();
        login.enter("driver1", "definitely-wrong").submit();
        assertThat(login.error()).isEqualTo("Invalid username or password");
        assertThat(login.currentPath()).isEqualTo("/login");
    }

    @Test
    void submit_is_disabled_until_both_fields_are_filled() {
        LoginPage login = pages().login().open();
        assertThat(login.submitEnabled()).isFalse();
        login.enter("driver1", "");
        assertThat(login.submitEnabled()).isFalse();
    }

    @Test
    void a_deep_link_without_a_session_redirects_to_login(WebDriver browser) {
        browser.get(pages().baseUrl() + "/clerk");
        assertThat(pages().login().waitUntilLoaded().currentPath()).isEqualTo("/login");
    }

    @Test
    void a_driver_who_opens_the_clerk_page_is_sent_back_to_their_own(WebDriver browser) {
        pages().driverSignedInViaApi(fx.newDriver());
        browser.get(pages().baseUrl() + "/clerk");
        assertThat(pages().driverPage().waitUntilLoaded().currentPath()).isEqualTo("/driver");
    }

    @Test
    void signing_out_returns_to_login() {
        pages().driverSignedInViaApi(fx.newDriver()).logout();
        assertThat(pages().login().waitUntilLoaded().currentPath()).isEqualTo("/login");
    }
}
