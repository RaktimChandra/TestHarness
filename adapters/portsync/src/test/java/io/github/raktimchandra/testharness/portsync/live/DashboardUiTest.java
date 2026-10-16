package io.github.raktimchandra.testharness.portsync.live;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import io.github.raktimchandra.testharness.portsync.Models.Alert;
import io.github.raktimchandra.testharness.portsync.ui.AlertsPage;
import io.github.raktimchandra.testharness.portsync.ui.ContainerLookupPage;
import io.github.raktimchandra.testharness.portsync.ui.OverviewPage;
import io.github.raktimchandra.testharness.portsync.ui.PortSyncPages;
import io.github.raktimchandra.testharness.ui.a11y.A11yGate;
import io.github.raktimchandra.testharness.ui.a11y.A11yScanner;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.junit.BrowserExtension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;

/** The PortSync dashboard in a real browser, with the framework's page objects, healing and axe. */
@Tag("ui")
@ExtendWith(BrowserExtension.class)
@DisplayName("PortSync dashboard (UI)")
class DashboardUiTest extends LiveTest {

    private PortSyncPages pages() {
        return PortSyncPages.current();
    }

    @Test
    @DisplayName("The overview's open-alerts figure matches the API")
    void overviewShowsTheApisFigures() {
        OverviewPage overview = pages().overview().open();
        assertThat(overview.heading()).isEqualTo("Terminal overview");
        long api = core.overview().openAlerts();
        // The page refreshes over SSE; the figure may move by the alerts opened in between.
        assertThat(Long.parseLong(overview.kpi("Open alerts"))).isBetween(Math.max(0, api - 2), api + 2);
    }

    @Test
    @DisplayName("The live check-digit verdict agrees with the independent oracle on 20 numbers")
    void checkDigitVerdictAgreesWithTheOracle() {
        ContainerLookupPage page = pages().containers().open();
        Random random = new Random(46);
        List<String> disagreements = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String valid = i % 2 == 0 ? ContainerNumbers.randomAnyCategory(random) : ContainerNumbers.withRemainderTen(random);
            String wrong = ContainerNumbers.withWrongCheckDigit(valid);
            if (!page.enter(valid).verdictBecomes("Valid ISO 6346 number") || !page.lookUpEnabled()) {
                disagreements.add(valid + " should be valid: " + page.verdict());
            }
            String expected = "Check digit should be " + valid.charAt(10) + ", not " + wrong.charAt(10);
            if (!page.enter(wrong).verdictBecomes(expected) || page.lookUpEnabled()) {
                disagreements.add(wrong + " should say '" + expected + "': " + page.verdict());
            }
        }
        assertThat(disagreements).isEmpty();
    }

    @Test
    @DisplayName("Looking up a stacked container shows its slot; an unknown valid number says so")
    void containerLookup() {
        String number = containerInYard("B");
        ContainerLookupPage page = pages().containers().open().enter(number).lookUp();
        assertThat(page.resultNumber()).isEqualTo(number);
        assertThat(page.fact("Location")).startsWith("B-");
        assertThat(page.history()).isNotEmpty();

        String unknown = neverSeenNumber(new Random(47));
        page = pages().containers().open().enter(unknown).lookUp();
        assertThat(page.notice()).isEqualTo(unknown + " is a valid number, but it has never been seen at this terminal.");
    }

    @Test
    @DisplayName("An operator acknowledges a live alert from the alerts page")
    void acknowledgeAnAlertFromTheDashboard() {
        String crane = healthyCrane();
        simulator.faultCrane(crane, "TESTHARNESS_UI");
        try {
            Alert open = AlertLifecycleTest.openAlertFor(this, crane);
            AlertsPage alerts = pages().alerts().open();
            alerts.waitForState(open.id(), "Open").acknowledge(open.id()).waitForState(open.id(), "Acknowledged");
            assertThat(alerts.acknowledgeEnabled(open.id())).as("acknowledge button after acknowledging").isFalse();
            assertThat(core.alerts("ACKNOWLEDGED")).extracting(Alert::id).contains(open.id());
        } finally {
            simulator.recoverCrane(crane);
        }
    }

    @Test
    @Tag("a11y")
    @DisplayName("axe-core: overview, container lookup (with a verdict) and alerts")
    void accessibility(WebDriver driver) {
        A11yGate gate = A11yGate.from(HarnessConfig.get());
        String browser = Drivers.session().orElseThrow().settings().browser().id();
        A11yScanner scanner = new A11yScanner(driver, browser, Path.of("target", "a11y"));
        pages().overview().open();
        scanner.assertWithin("portsync-overview", gate);
        pages().containers().open().enter("CSQU3054384");
        scanner.assertWithin("portsync-containers-invalid", gate);
        pages().alerts().open();
        scanner.assertWithin("portsync-alerts", gate);
    }
}
