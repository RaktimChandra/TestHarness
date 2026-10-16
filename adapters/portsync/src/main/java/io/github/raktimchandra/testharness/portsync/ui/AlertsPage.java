package io.github.raktimchandra.testharness.portsync.ui;

import io.github.raktimchandra.testharness.ui.heal.Target;
import io.github.raktimchandra.testharness.ui.page.BasePage;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** The alerts table with its acknowledge and resolve actions. */
public final class AlertsPage extends BasePage<AlertsPage> {

    private static final Target ALL = Target.named("'All' filter").accessibleName("button", "All")
            .text("button", "All");

    AlertsPage(WebDriver driver, Waits waits, String baseUrl) {
        super(driver, waits, baseUrl);
    }

    @Override
    protected By readyLocator() {
        return By.xpath("//h1[normalize-space(.)='Alerts']");
    }

    @Override
    protected String path() {
        return "/alerts";
    }

    public AlertsPage showAll() {
        click(ALL);
        return this;
    }

    private static Target button(String action, long id) {
        return Target.named(action + " button of alert " + id).accessibleName("button", action + " alert " + id);
    }

    /** The state pill in the alert's row (the row is found by its acknowledge button's label). */
    private static By statePill(long id) {
        return By.xpath("//table[contains(@class,'data')]//tr[.//button[@aria-label='Acknowledge alert " + id
                + "']]/td[6]//span[contains(@class,'pill')]");
    }

    public AlertsPage acknowledge(long id) {
        click(button("Acknowledge", id));
        return this;
    }

    public AlertsPage resolve(long id) {
        click(button("Resolve", id));
        return this;
    }

    /** Waits until the alert's state pill reads {@code state} ("Open", "Acknowledged", "Resolved"). */
    public AlertsPage waitForState(long id, String state) {
        waits.textContains(statePill(id), state);
        return this;
    }

    public String state(long id) {
        return text(statePill(id));
    }

    public boolean acknowledgeEnabled(long id) {
        return find(button("Acknowledge", id)).isEnabled();
    }
}
