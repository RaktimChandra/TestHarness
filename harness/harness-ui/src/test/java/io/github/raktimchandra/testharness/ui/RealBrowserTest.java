package io.github.raktimchandra.testharness.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.evidence.UiEvidence;
import io.github.raktimchandra.testharness.ui.junit.BrowserExtension;
import io.github.raktimchandra.testharness.ui.page.Locators;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

/**
 * Exercises the UI layer against a real browser and a static page that changes itself after
 * a delay — proving explicit waits, BiDi console capture and failure evidence end to end.
 */
@Tag("browser")
@ExtendWith(BrowserExtension.class)
class RealBrowserTest {

    @TempDir
    Path evidence;

    private static String page() throws URISyntaxException {
        return RealBrowserTest.class.getResource("/delayed.html").toURI().toString();
    }

    @Test
    void explicitWaitsFollowAsynchronousChanges(WebDriver driver, Waits waits) throws Exception {
        driver.get(page());
        waits.text(Locators.testId("status"), "ready");
        waits.clickable(Locators.testId("go")).click();
        waits.text(Locators.testId("status"), "clicked");
        assertThat(waits.count(Locators.testIdPrefix("item-"), 3)).hasSize(3);
    }

    @Test
    void consoleMessagesAndJavaScriptErrorsAreCaptured(WebDriver driver, Waits waits) throws Exception {
        driver.get(page());
        waits.text(Locators.testId("status"), "ready");
        var console = Drivers.session().orElseThrow().console();
        waits.withTimeout(Duration.ofSeconds(5)).until("console capture",
                d -> console.lines().stream().anyMatch(l -> l.contains("page booted"))
                        && console.lines().stream().anyMatch(l -> l.contains("deliberate failure")));
        assertThat(console.available()).isTrue();
        assertThat(console.render()).contains("[console.", "[js-error]");
    }

    @Test
    void evidenceIsWrittenForDiagnosis(WebDriver driver, Waits waits) throws Exception {
        driver.get(page());
        waits.text(Locators.testId("status"), "ready");
        UiEvidence.Captured c = UiEvidence.capture(driver, Drivers.session().orElseThrow().console(),
                "Some Test / with:odd chars", evidence);
        assertThat(c.screenshot()).isTrue();
        assertThat(c.source()).isTrue();
        assertThat(c.directory().getFileName().toString()).isEqualTo("Some_Test_with_odd_chars");
        assertThat(c.directory().resolve("screenshot.png")).isNotEmptyFile();
        assertThat(c.directory().resolve("page.html")).content().contains("data-testid=\"status\"");
    }

    @Test
    void axeFindsTheKnownViolationsOnAFixturePage(WebDriver driver) throws Exception {
        driver.get(RealBrowserTest.class.getResource("/a11y-bad.html").toURI().toString());
        var scanner = new io.github.raktimchandra.testharness.ui.a11y.A11yScanner(driver, "test", evidence);
        var report = scanner.scan("fixture");
        assertThat(report.violations()).extracting(v -> v.rule())
                .contains("image-alt", "label", "color-contrast");
        assertThat(evidence.resolve("fixture-test.json")).exists();
        assertThatThrownBy(() -> scanner.assertWithin("fixture",
                new io.github.raktimchandra.testharness.ui.a11y.A11yGate(0, 0, -1, -1)))
                .hasMessageContaining("Accessibility gate failed on 'fixture'");
    }

    @Test
    void visualCapturesAreStabilisedSoFocusRingsDoNotLeakIntoBaselines(WebDriver driver, Waits waits) throws Exception {
        driver.get(RealBrowserTest.class.getResource("/focus-ring.html").toURI().toString());
        var settings = new io.github.raktimchandra.testharness.ui.visual.VisualSettings(evidence.resolve("baselines"),
                evidence.resolve("out"), "test", 0.0, 0.1, true, false);
        var box = waits.visible(Locators.testId("box"));
        new io.github.raktimchandra.testharness.ui.visual.VisualCheck(driver, settings).matches("unfocused", box);

        driver.navigate().refresh();
        box = waits.visible(Locators.testId("box"));
        waits.clickable(Locators.testId("name")).click(); // focus ring + caret
        var compare = new io.github.raktimchandra.testharness.ui.visual.VisualSettings(evidence.resolve("baselines"),
                evidence.resolve("out"), "test", 0.0, 0.1, false, true);
        java.nio.file.Files.copy(evidence.resolve("baselines/test/unfocused.png"),
                evidence.resolve("baselines/test/focused.png"));
        assertThat(new io.github.raktimchandra.testharness.ui.visual.VisualCheck(driver, compare).matches("focused", box))
                .isEqualTo(io.github.raktimchandra.testharness.ui.visual.VisualCheck.Outcome.MATCHED);
    }

    @Test
    void timeoutsAreFastAndDescriptive(WebDriver driver, Waits waits) throws Exception {
        driver.get(page());
        long start = System.nanoTime();
        assertThatThrownBy(() -> waits.withTimeout(Duration.ofMillis(400)).visible(Locators.testId("never")))
                .isInstanceOf(TimeoutException.class).hasMessageContaining("never");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }
}
