package io.github.raktimchandra.testharness.ui.page;

import io.github.raktimchandra.testharness.reporting.Report;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opening a URL, with one documented exception to "no retries": ChromeDriver and EdgeDriver can
 * report {@code Timed out receiving message from renderer} when a renderer process stops
 * answering the driver during a navigation on a loaded machine (seen on the Windows runner in
 * Edge, 2026-10-20). That is the browser's plumbing failing, not the page: the navigation is
 * repeated once, the repeat is logged and added to the report, and a second failure fails the
 * test. Every other timeout — a page that is genuinely slow — fails at once.
 */
public final class Navigation {

    private static final Logger log = LoggerFactory.getLogger(Navigation.class);

    static final String RENDERER_TIMEOUT = "Timed out receiving message from renderer";

    private Navigation() {}

    public static void open(WebDriver driver, String url) {
        try {
            driver.get(url);
        } catch (TimeoutException e) {
            if (e.getMessage() == null || !e.getMessage().contains(RENDERER_TIMEOUT)) {
                throw e;
            }
            String first = e.getMessage().lines().findFirst().orElse(RENDERER_TIMEOUT);
            log.warn("Browser renderer did not answer while opening {} ({}); opening it once more", url, first);
            Report.note("Navigation to " + url + " repeated once after a renderer timeout: " + first);
            driver.navigate().to(url);
        }
    }
}
