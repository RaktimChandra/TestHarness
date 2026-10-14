package io.github.raktimchandra.testharness.ui.page;

import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.By;
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

    protected BasePage(WebDriver driver, Waits waits, String baseUrl) {
        this.driver = driver;
        this.waits = waits;
        this.baseUrl = baseUrl;
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

    public String currentPath() {
        String url = driver.getCurrentUrl();
        return url == null ? "" : url.replaceFirst("^https?://[^/]+", "");
    }
}
