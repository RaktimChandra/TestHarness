package io.github.raktimchandra.testharness.gate.ui;

import io.github.raktimchandra.testharness.gate.api.GateApi;
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
        return new GatePages(d, new Waits(d, s.explicitWait(), s.pollInterval()), GateApi.get().baseUrl());
    }

    public LoginPage login() {
        return new LoginPage(driver, waits, baseUrl);
    }

    public DriverPage driverPage() {
        return new DriverPage(driver, waits, baseUrl);
    }

    public ClerkPage clerkPage() {
        return new ClerkPage(driver, waits, baseUrl);
    }
}
