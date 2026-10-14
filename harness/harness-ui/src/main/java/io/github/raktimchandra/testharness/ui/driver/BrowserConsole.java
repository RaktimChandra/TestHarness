package io.github.raktimchandra.testharness.ui.driver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.HasBiDi;
import org.openqa.selenium.bidi.module.LogInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the browser's console messages and uncaught JavaScript errors over WebDriver BiDi
 * (works the same in Chrome, Edge and Firefox). If the driver has no BiDi session the
 * console is reported as unavailable rather than silently empty.
 */
public final class BrowserConsole implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BrowserConsole.class);

    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private final LogInspector inspector;
    private final String unavailableReason;

    private BrowserConsole(LogInspector inspector, String unavailableReason) {
        this.inspector = inspector;
        this.unavailableReason = unavailableReason;
    }

    public static BrowserConsole attach(WebDriver driver) {
        if (!(driver instanceof HasBiDi)) {
            return new BrowserConsole(null, "driver does not support WebDriver BiDi");
        }
        try {
            LogInspector inspector = new LogInspector(driver);
            BrowserConsole console = new BrowserConsole(inspector, null);
            inspector.onConsoleEntry(e -> console.lines.add(
                    "[console." + e.getLevel() + "] " + e.getText()));
            inspector.onJavaScriptException(e -> console.lines.add(
                    "[js-error] " + e.getText()));
            return console;
        } catch (RuntimeException e) {
            log.warn("Browser console capture unavailable: {}", e.toString());
            return new BrowserConsole(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public boolean available() {
        return inspector != null;
    }

    public List<String> lines() {
        synchronized (lines) {
            return List.copyOf(lines);
        }
    }

    public String render() {
        if (!available()) {
            return "(browser console unavailable: " + unavailableReason + ")";
        }
        List<String> copy = lines();
        return copy.isEmpty() ? "(no console messages)" : String.join("\n", copy);
    }

    @Override
    public void close() {
        if (inspector != null) {
            try {
                inspector.close();
            } catch (RuntimeException e) {
                log.debug("closing log inspector: {}", e.toString());
            }
        }
    }
}
