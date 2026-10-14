package io.github.raktimchandra.testharness.ui.driver;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.time.Duration;
import java.util.Optional;

/**
 * Browser settings resolved from configuration ({@code ui.*} keys).
 *
 * @param binary  optional browser executable (otherwise the installed browser is used)
 * @param driver  optional driver executable (otherwise Selenium Manager resolves one)
 * @param sessionAttempts how many times to try starting a browser session (1–{@value
 *                        io.github.raktimchandra.testharness.core.retry.Retry#MAX_ATTEMPTS_CAP})
 */
public record UiSettings(Browser browser, boolean headless, int width, int height, Duration explicitWait,
                         Duration pollInterval, Duration pageLoadTimeout, Optional<String> gridUrl,
                         Optional<String> binary, Optional<String> driver, boolean noSandbox,
                         int sessionAttempts) {

    public static UiSettings from(HarnessConfig c) {
        Browser browser = Browser.parse(c.string("ui.browser", "chrome"));
        String[] size = c.string("ui.window", "1366x900").toLowerCase().split("x");
        if (size.length != 2) {
            throw new IllegalArgumentException("ui.window must look like 1366x900");
        }
        return new UiSettings(browser,
                c.bool("ui.headless", true),
                Integer.parseInt(size[0].strip()), Integer.parseInt(size[1].strip()),
                c.duration("ui.explicit-wait", Duration.ofSeconds(10)),
                c.duration("ui.poll-interval", Duration.ofMillis(100)),
                c.duration("ui.page-load-timeout", Duration.ofSeconds(30)),
                c.find("ui.grid-url"),
                c.find("ui.binary." + browser.id()),
                c.find("ui.driver-path." + browser.id()),
                c.bool("ui.no-sandbox", false),
                c.integer("ui.session-attempts", 2));
    }
}
