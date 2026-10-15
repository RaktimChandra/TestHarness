package io.github.raktimchandra.testharness.ui.page;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.heal.Healer;
import io.github.raktimchandra.testharness.ui.heal.HealingMode;
import io.github.raktimchandra.testharness.ui.heal.Target;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Base for page objects. A page knows how to recognise itself ({@link #readyLocator()}) and
 * exposes intention-revealing actions; tests never touch locators directly.
 */
public abstract class BasePage<P extends BasePage<P>> {

    protected final WebDriver driver;
    protected final Waits waits;
    protected final String baseUrl;
    protected final Healer healer;

    protected BasePage(WebDriver driver, Waits waits, String baseUrl) {
        this.driver = driver;
        this.waits = waits;
        this.baseUrl = baseUrl;
        this.healer = new Healer(driver, HealingMode.parse(HarnessConfig.get().string("ui.healing", "report")));
    }

    /** An element that is visible only once this page has rendered. */
    protected abstract By readyLocator();

    protected abstract String path();

    @SuppressWarnings("unchecked")
    public P open() {
        Report.step("Open " + getClass().getSimpleName() + " (" + path() + ")", () -> driver.get(baseUrl + path()));
        return waitUntilLoaded();
    }

    @SuppressWarnings("unchecked")
    public P waitUntilLoaded() {
        waits.visible(readyLocator());
        return (P) this;
    }

    protected WebElement find(By by) {
        return waits.visible(by);
    }

    protected void click(By by) {
        waits.clickable(by).click();
    }

    protected void type(By by, String text) {
        WebElement e = waits.visible(by);
        e.clear();
        e.sendKeys(text);
    }

    protected String text(By by) {
        return waits.visible(by).getText().strip();
    }

    /** Visible element for a {@link Target}; a fallback is used (and reported as a heal) only if the primary fails. */
    protected WebElement find(Target target) {
        return locate(target, false);
    }

    protected void click(Target target) {
        locate(target, true).click();
    }

    protected void type(Target target, String text) {
        WebElement e = locate(target, false);
        e.clear();
        e.sendKeys(text);
    }

    private WebElement locate(Target target, boolean clickable) {
        try {
            Healer.Located located = waits.until("'" + target.name() + "' (" + target.primary().describe() + ") to be "
                    + (clickable ? "clickable" : "visible"), d -> healer.attempt(d, target)
                    .filter(l -> l.element().isDisplayed() && (!clickable || l.element().isEnabled()))
                    .orElse(null));
            return healer.accept(target, located);
        } catch (TimeoutException e) {
            NoSuchElementException notFound = healer.notFound(driver, target);
            notFound.addSuppressed(e);
            throw notFound;
        }
    }

    public String currentPath() {
        String url = driver.getCurrentUrl();
        return url == null ? "" : url.replaceFirst("^https?://[^/]+", "");
    }
}
