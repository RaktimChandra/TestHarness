package io.github.raktimchandra.testharness.ui.wait;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Explicit waits only. Every wait names the condition it is waiting for, so a timeout says
 * what never became true ("button [data-testid='book-submit'] to be clickable") instead of
 * a bare stack trace. Implicit waits are switched off by the driver factory, and fixed
 * sleeps are banned by an ArchUnit rule.
 */
public final class Waits {

    private final WebDriver driver;
    private final Duration timeout;
    private final Duration poll;

    public Waits(WebDriver driver, Duration timeout, Duration poll) {
        this.driver = Objects.requireNonNull(driver, "driver");
        this.timeout = timeout;
        this.poll = poll;
    }

    public Waits withTimeout(Duration newTimeout) {
        return new Waits(driver, newTimeout, poll);
    }

    /** The general form: wait until {@code condition} returns a non-null, non-false value. */
    public <T> T until(String description, Function<WebDriver, T> condition) {
        WebDriverWait wait = new WebDriverWait(driver, timeout, poll);
        wait.ignoring(StaleElementReferenceException.class).ignoring(NoSuchElementException.class);
        wait.withMessage("waiting " + timeout.toMillis() + " ms for " + description);
        return wait.until(condition);
    }

    public WebElement visible(By by) {
        return until(by + " to be visible", ExpectedConditions.visibilityOfElementLocated(by));
    }

    public WebElement clickable(By by) {
        return until(by + " to be clickable", ExpectedConditions.elementToBeClickable(by));
    }

    public WebElement present(By by) {
        return until(by + " to be present", ExpectedConditions.presenceOfElementLocated(by));
    }

    public void gone(By by) {
        until(by + " to disappear", ExpectedConditions.invisibilityOfElementLocated(by));
    }

    public void text(By by, String expected) {
        until(by + " to have text '" + expected + "'", d -> {
            WebElement e = d.findElement(by);
            return expected.equals(e.getText().strip());
        });
    }

    public WebElement textContains(By by, String fragment) {
        return until(by + " to contain text '" + fragment + "'", d -> {
            WebElement e = d.findElement(by);
            return e.isDisplayed() && e.getText().contains(fragment) ? e : null;
        });
    }

    public void attribute(By by, String name, String expected) {
        until(by + " to have " + name + "='" + expected + "'",
                d -> expected.equals(d.findElement(by).getDomAttribute(name)));
    }

    public List<WebElement> count(By by, int expected) {
        return until(expected + " elements matching " + by, d -> {
            List<WebElement> found = d.findElements(by);
            return found.size() == expected ? found : null;
        });
    }

    public void urlContains(String fragment) {
        until("URL to contain '" + fragment + "'", ExpectedConditions.urlContains(fragment));
    }

    /** True if the condition holds within the timeout; false (never an exception) otherwise. */
    public boolean holdsWithin(String description, Function<WebDriver, Boolean> condition) {
        try {
            until(description, condition);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }
}
