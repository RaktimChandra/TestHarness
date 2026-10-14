package io.github.raktimchandra.testharness.gate.ui;

import io.github.raktimchandra.testharness.gate.api.GateApi;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.github.raktimchandra.testharness.reporting.Report;
import org.openqa.selenium.JavascriptExecutor;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.driver.UiSettings;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.WebDriver;

/** Creates Gate page objects bound to the current thread's browser and the SUT's URL. */
public final class GatePages {

    private final WebDriver driver;
    private final Waits waits;
    private final String baseUrl;

    private GatePages(WebDriver driver, Waits waits, String baseUrl) {
        this.driver = driver;
        this.waits = waits;
        this.baseUrl = baseUrl;
    }

    public static GatePages current() {
        UiSettings s = Drivers.session().orElseThrow(() -> new IllegalStateException("no browser session")).settings();
        WebDriver d = Drivers.current();
        // Browsers on a Selenium Grid reach the app by its container name (sut.browser-base-url),
        // while the tests themselves call the API on the host.
        String browserUrl = io.github.raktimchandra.testharness.core.config.HarnessConfig.get()
                .string("sut.browser-base-url", GateApi.get().baseUrl());
        return new GatePages(d, new Waits(d, s.explicitWait(), s.pollInterval()), browserUrl);
    }

    public LoginPage login() {
        return new LoginPage(driver, waits, baseUrl);
    }

    public DriverPage driverPage() {
        return new DriverPage(driver, waits, baseUrl);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public ClerkPage clerkPage() {
        return new ClerkPage(driver, waits, baseUrl);
    }

    /**
     * Signs in without using the login form: obtains a token from the API and puts it where
     * the UI keeps it. Journeys other than login itself start this way, so a broken login
     * form fails the login tests only, not every UI test (and the suite runs faster).
     */
    public DriverPage driverSignedInViaApi(GateUser user) {
        signIn(user);
        return driverPage().open();
    }

    public ClerkPage clerkSignedInViaApi(GateUser user) {
        signIn(user);
        return clerkPage().open();
    }

    private void signIn(GateUser user) {
        Report.step("Sign in " + user.username() + " via API token", () -> {
            Models.Login login = GateApi.get().login(user.username(), user.credentials().password().reveal())
                    .as(Models.Login.class);
            driver.get(baseUrl + "/login");
            waits.visible(io.github.raktimchandra.testharness.ui.page.Locators.testId("login-form"));
            ((JavascriptExecutor) driver).executeScript("sessionStorage.setItem('gate.token', arguments[0]);",
                    login.token());
        });
    }
}
