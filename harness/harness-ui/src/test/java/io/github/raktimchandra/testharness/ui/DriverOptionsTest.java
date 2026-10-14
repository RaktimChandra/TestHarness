package io.github.raktimchandra.testharness.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.ui.driver.Browser;
import io.github.raktimchandra.testharness.ui.driver.DriverFactory;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.driver.UiSettings;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.MutableCapabilities;

class DriverOptionsTest {

    private static UiSettings settings(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty("harness." + kv[i], kv[i + 1]);
        }
        return UiSettings.from(HarnessConfig.load(Map.of(), p, DriverOptionsTest.class.getClassLoader()));
    }

    @SuppressWarnings("unchecked")
    private static List<String> args(MutableCapabilities caps, String key) {
        Object o = caps.asMap().get(key);
        return (List<String>) ((Map<String, Object>) o).get("args");
    }

    @Test
    void settingsComeFromConfigWithSafeDefaults() {
        UiSettings s = settings("ui.browser", "firefox", "ui.window", "800x600", "ui.explicit-wait", "3s");
        assertThat(s.browser()).isEqualTo(Browser.FIREFOX);
        assertThat(s.width()).isEqualTo(800);
        assertThat(s.height()).isEqualTo(600);
        assertThat(s.explicitWait()).isEqualTo(Duration.ofSeconds(3));
        assertThat(s.headless()).isTrue();
        assertThat(s.gridUrl()).isEmpty();
    }

    @Test
    void chromeIsHeadlessSizedAndBiDiEnabled() {
        MutableCapabilities caps = DriverFactory.options(settings("ui.browser", "chrome", "ui.no-sandbox", "true"));
        assertThat(caps.getBrowserName()).isEqualTo("chrome");
        assertThat(args(caps, "goog:chromeOptions"))
                .contains("--headless=new", "--window-size=1366,900", "--no-sandbox");
        assertThat(caps.getCapability("webSocketUrl")).isEqualTo(true);
    }

    @Test
    void edgeAndFirefoxGetTheirOwnFlags() {
        MutableCapabilities edge = DriverFactory.options(settings("ui.browser", "edge", "ui.headless", "false"));
        assertThat(edge.getBrowserName()).isEqualTo("MicrosoftEdge");
        assertThat(args(edge, "ms:edgeOptions")).doesNotContain("--headless=new").doesNotContain("--no-sandbox");
        MutableCapabilities ff = DriverFactory.options(settings("ui.browser", "firefox"));
        assertThat(ff.getBrowserName()).isEqualTo("firefox");
        assertThat(args(ff, "moz:firefoxOptions")).contains("-headless", "--width=1366", "--height=900");
    }

    @Test
    void badValuesFailClearly() {
        assertThatThrownBy(() -> Browser.parse("safari")).hasMessageContaining("use chrome, firefox or edge");
        assertThatThrownBy(() -> settings("ui.window", "big")).hasMessageContaining("1366x900");
    }

    @Test
    void noBrowserOnThisThreadIsAClearError() {
        assertThatThrownBy(Drivers::current).hasMessageContaining("No browser started on thread");
        assertThat(Drivers.session()).isEmpty();
        Drivers.quit(); // no-op without a session
    }
}
