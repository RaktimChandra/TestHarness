package io.github.raktimchandra.testharness.ui.evidence;

import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.driver.BrowserConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures what a person would need to diagnose a failed UI test without re-running it:
 * a screenshot, the page HTML, the URL and the browser console. Each item is attached to
 * the report and also written under {@code target/evidence/<test>/}. Capture failures are
 * logged, never thrown, so they cannot mask the original test failure.
 */
public final class UiEvidence {

    private static final Logger log = LoggerFactory.getLogger(UiEvidence.class);

    private UiEvidence() {}

    public record Captured(Path directory, boolean screenshot, boolean source, boolean console) {}

    public static Captured capture(WebDriver driver, BrowserConsole console, String testName, Path baseDir) {
        Path dir = baseDir.resolve(safe(testName));
        boolean shot = false;
        boolean html = false;
        boolean logs = false;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Cannot create evidence directory {}: {}", dir, e.toString());
        }
        try {
            if (driver instanceof TakesScreenshot ts) {
                byte[] png = ts.getScreenshotAs(OutputType.BYTES);
                Report.png("Screenshot at failure", png);
                Files.write(dir.resolve("screenshot.png"), png);
                shot = true;
            }
        } catch (RuntimeException | IOException e) {
            log.warn("Screenshot capture failed: {}", e.toString());
        }
        try {
            String url = String.valueOf(driver.getCurrentUrl());
            String source = driver.getPageSource();
            Report.text("URL at failure", url);
            Report.html("Page source at failure", source);
            Files.writeString(dir.resolve("page.html"), "<!-- " + url + " -->\n" + source, StandardCharsets.UTF_8);
            html = true;
        } catch (RuntimeException | IOException e) {
            log.warn("Page source capture failed: {}", e.toString());
        }
        try {
            String rendered = console == null ? "(no console capture)" : console.render();
            Report.text("Browser console", rendered);
            Files.writeString(dir.resolve("console.log"), rendered, StandardCharsets.UTF_8);
            logs = console != null && console.available();
        } catch (RuntimeException | IOException e) {
            log.warn("Console capture failed: {}", e.toString());
        }
        return new Captured(dir, shot, html, logs);
    }

    static String safe(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
