package io.github.raktimchandra.testharness.ui.driver;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeDriverService;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.io.File;

/** Creates local or Grid WebDrivers with consistent, explicit settings. */
public final class DriverFactory {

    private DriverFactory() {}

    public static WebDriver create(UiSettings s) {
        Capabilities options = options(s);
        WebDriver driver;
        if (s.gridUrl().isPresent()) {
            try {
                driver = RemoteWebDriver.builder().oneOf(options).address(URI.create(s.gridUrl().get()).toURL())
                        .augmentUsing(new org.openqa.selenium.remote.Augmenter()).build();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("ui.grid-url is not a valid URL: " + s.gridUrl().get(), e);
            }
        } else {
            driver = switch (s.browser()) {
                case CHROME -> s.driver().isPresent()
                        ? new ChromeDriver(new ChromeDriverService.Builder().usingDriverExecutable(new File(s.driver().get())).build(), (ChromeOptions) options)
                        : new ChromeDriver((ChromeOptions) options);
                case EDGE -> s.driver().isPresent()
                        ? new EdgeDriver(new EdgeDriverService.Builder().usingDriverExecutable(new File(s.driver().get())).build(), (EdgeOptions) options)
                        : new EdgeDriver((EdgeOptions) options);
                case FIREFOX -> s.driver().isPresent()
                        ? new FirefoxDriver(new GeckoDriverService.Builder().usingDriverExecutable(new File(s.driver().get())).build(), (FirefoxOptions) options)
                        : new FirefoxDriver((FirefoxOptions) options);
            };
        }
        driver.manage().timeouts().implicitlyWait(Duration.ZERO); // explicit waits only
        driver.manage().timeouts().pageLoadTimeout(s.pageLoadTimeout());
        return driver;
    }

    /** Browser options for the settings; separated out so they can be unit-tested without a browser. */
    public static MutableCapabilities options(UiSettings s) {
        MutableCapabilities caps = switch (s.browser()) {
            case CHROME -> chromium(new ChromeOptions(), s);
            case EDGE -> chromium(new EdgeOptions(), s);
            case FIREFOX -> {
                FirefoxOptions o = new FirefoxOptions();
                if (s.headless()) {
                    o.addArguments("-headless");
                }
                o.addArguments("--width=" + s.width(), "--height=" + s.height());
                s.binary().ifPresent(o::setBinary);
                yield o;
            }
        };
        // WebDriver BiDi gives cross-browser console and JS-error capture.
        caps.setCapability("webSocketUrl", true);
        return caps;
    }

    private static <T extends ChromiumOptions<?>> T chromium(T o, UiSettings s) {
        if (s.headless()) {
            o.addArguments("--headless=new");
        }
        o.addArguments("--window-size=" + s.width() + "," + s.height(), "--disable-search-engine-choice-screen",
                "--no-first-run", "--disable-gpu");
        if (s.noSandbox()) {
            o.addArguments("--no-sandbox", "--disable-dev-shm-usage");
        }
        s.binary().ifPresent(o::setBinary);
        return o;
    }
}
