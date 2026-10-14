package io.github.raktimchandra.testharness.ui.driver;

import io.github.raktimchandra.testharness.core.logging.TestLog;
import java.util.Optional;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One WebDriver per thread. Parallel JUnit workers each get their own browser; page objects
 * call {@link #current()} and never share a driver across threads.
 */
public final class Drivers {

    private static final Logger log = LoggerFactory.getLogger(Drivers.class);
    private static final ThreadLocal<Session> SESSION = new ThreadLocal<>();

    private Drivers() {}

    public record Session(WebDriver driver, BrowserConsole console, UiSettings settings) {}

    public static Session start(UiSettings settings) {
        Session existing = SESSION.get();
        if (existing != null) {
            return existing;
        }
        long t0 = System.nanoTime();
        WebDriver driver = DriverFactory.create(settings);
        Session s = new Session(driver, BrowserConsole.attach(driver), settings);
        SESSION.set(s);
        TestLog.put("browser", settings.browser().id());
        log.info("Started {} ({}headless{}) in {} ms", settings.browser().id(), settings.headless() ? "" : "not ",
                settings.gridUrl().map(g -> ", grid " + g).orElse(""), (System.nanoTime() - t0) / 1_000_000);
        return s;
    }

    public static WebDriver current() {
        Session s = SESSION.get();
        if (s == null) {
            throw new IllegalStateException("No browser started on thread " + Thread.currentThread().getName()
                    + "; use BrowserExtension or Drivers.start(...)");
        }
        return s.driver();
    }

    public static Optional<Session> session() {
        return Optional.ofNullable(SESSION.get());
    }

    public static void quit() {
        Session s = SESSION.get();
        SESSION.remove();
        TestLog.remove("browser");
        if (s != null) {
            s.console().close();
            try {
                s.driver().quit();
            } catch (RuntimeException e) {
                log.warn("driver.quit() failed: {}", e.toString());
            }
        }
    }
}
