package io.github.raktimchandra.testharness.ui.driver;

import io.github.raktimchandra.testharness.core.logging.TestLog;
import io.github.raktimchandra.testharness.core.retry.Backoff;
import io.github.raktimchandra.testharness.core.retry.Retry;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One WebDriver per thread. Parallel JUnit workers each get their own browser; page objects
 * call {@link #current()} and never share a driver across threads.
 *
 * <p>Starting a browser is infrastructure, so it is the one UI operation that is retried:
 * up to {@code ui.session-attempts} (default 2) times, and only on
 * {@link SessionNotCreatedException}. The first session per browser and target in a JVM is
 * created alone, so driver resolution (Selenium Manager) and first-launch set-up are not
 * raced by every parallel worker at once; later sessions start concurrently. Both were
 * added after four parallel first starts of Edge all failed with SessionNotCreated in CI
 * run 38611150889 while every later start succeeded.
 */
public final class Drivers {

    private static final Logger log = LoggerFactory.getLogger(Drivers.class);
    private static final ThreadLocal<Session> SESSION = new ThreadLocal<>();

    private static final Set<String> STARTED_ONCE = ConcurrentHashMap.newKeySet();
    private static final Object FIRST_START = new Object();

    private Drivers() {}

    public record Session(WebDriver driver, BrowserConsole console, UiSettings settings) {}

    public static Session start(UiSettings settings) {
        Session existing = SESSION.get();
        if (existing != null) {
            return existing;
        }
        long t0 = System.nanoTime();
        WebDriver driver = create(settings);
        Session s = new Session(driver, BrowserConsole.attach(driver), settings);
        SESSION.set(s);
        TestLog.put("browser", settings.browser().id());
        log.info("Started {} {} ({}headless{}) in {} ms", settings.browser().id(), version(driver),
                settings.headless() ? "" : "not ", settings.gridUrl().map(g -> ", grid " + g).orElse(""),
                (System.nanoTime() - t0) / 1_000_000);
        return s;
    }

    /** The version the browser reports, which is what a rendering depends on. */
    static String version(WebDriver driver) {
        if (driver instanceof org.openqa.selenium.HasCapabilities c) {
            String v = c.getCapabilities().getBrowserVersion();
            return v == null || v.isBlank() ? "(version unknown)" : v;
        }
        return "(version unknown)";
    }

    private static WebDriver create(UiSettings settings) {
        return create(settings, DriverFactory::create, Backoff.fixed(Duration.ofSeconds(2)));
    }

    /** Start policy with the factory and backoff injectable, so it is testable without a browser. */
    static WebDriver create(UiSettings settings, java.util.function.Function<UiSettings, WebDriver> factory,
                            Backoff backoff) {
        String key = settings.browser().id() + "@" + settings.gridUrl().orElse("local");
        if (!STARTED_ONCE.contains(key)) {
            synchronized (FIRST_START) {
                // Re-check: workers that queued here while the first start ran must not
                // start inside the lock, or every start would be serialised.
                if (!STARTED_ONCE.contains(key)) {
                    WebDriver driver = createWithRetry(settings, factory, backoff);
                    STARTED_ONCE.add(key);
                    return driver;
                }
            }
        }
        return createWithRetry(settings, factory, backoff);
    }

    private static WebDriver createWithRetry(UiSettings settings,
                                             java.util.function.Function<UiSettings, WebDriver> factory,
                                             Backoff backoff) {
        return Retry.of("start " + settings.browser().id())
                .maxAttempts(settings.sessionAttempts())
                .backoff(backoff)
                .retryOn(SessionNotCreatedException.class)
                .build()
                .call(() -> factory.apply(settings));
    }

    /** Forgets which browsers have started (tests only). */
    static void resetStartHistory() {
        STARTED_ONCE.clear();
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
