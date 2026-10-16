package io.github.raktimchandra.testharness.portsync.ui;

import io.github.raktimchandra.testharness.portsync.PortSync;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.driver.UiSettings;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.WebDriver;

/** PortSync dashboard page objects bound to the current thread's browser. */
public final class PortSyncPages {

    private final WebDriver driver;
    private final Waits waits;
    private final String baseUrl;

    private PortSyncPages(WebDriver driver, Waits waits, String baseUrl) {
        this.driver = driver;
        this.waits = waits;
        this.baseUrl = baseUrl;
    }

    public static PortSyncPages current() {
        UiSettings s = Drivers.session().orElseThrow(() -> new IllegalStateException("no browser session")).settings();
        WebDriver d = Drivers.current();
        return new PortSyncPages(d, new Waits(d, s.explicitWait(), s.pollInterval()), PortSync.get().uiUrl());
    }

    public OverviewPage overview() {
        return new OverviewPage(driver, waits, baseUrl);
    }

    public ContainerLookupPage containers() {
        return new ContainerLookupPage(driver, waits, baseUrl);
    }

    public AlertsPage alerts() {
        return new AlertsPage(driver, waits, baseUrl);
    }

    public Waits waits() {
        return waits;
    }
}
