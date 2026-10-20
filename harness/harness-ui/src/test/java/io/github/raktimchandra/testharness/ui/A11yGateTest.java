package io.github.raktimchandra.testharness.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.ui.a11y.A11yGate;
import io.github.raktimchandra.testharness.ui.a11y.A11yReport;
import io.github.raktimchandra.testharness.ui.a11y.A11yReport.Violation;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class A11yGateTest {

    private static final A11yReport REPORT = new A11yReport("login", "chrome", "http://x/login", List.of(
            new Violation("color-contrast", "serious", "Contrast", "u", 3, List.of("#a")),
            new Violation("region", "moderate", "Landmarks", "u", 1, List.of("#b")),
            new Violation("image-alt", "critical", "Alt text", "u", 2, List.of("img"))));

    @Test
    void countsEveryImpactIncludingZeroes() {
        assertThat(REPORT.countsByImpact())
                .containsExactly(Map.entry("critical", 1), Map.entry("serious", 1), Map.entry("moderate", 1),
                        Map.entry("minor", 0));
    }

    @Test
    void defaultGateBlocksCriticalAndSeriousOnly() {
        A11yGate gate = A11yGate.from(HarnessConfig.load(Map.of(), new Properties(), getClass().getClassLoader()));
        assertThat(gate).isEqualTo(new A11yGate(0, 0, -1, -1));
        assertThat(gate.breaches(REPORT)).containsExactly(
                "1 critical (max 0): image-alt (2 elements: img)",
                "1 serious (max 0): color-contrast (3 elements: #a)");
    }

    @Test
    void reportOnlyThresholdsNeverBreach() {
        assertThat(new A11yGate(-1, -1, -1, -1).breaches(REPORT)).isEmpty();
        assertThat(new A11yGate(1, 1, 0, 0).breaches(REPORT)).containsExactly("1 moderate (max 0): region (1 element: #b)");
    }

    @Test
    void knownIssuesAreReportedButNotCountedUnlessANewElementAppears() {
        A11yGate gate = A11yGate.from(HarnessConfig.load(Map.of(),
                props("harness.a11y.known-issues", "color-contrast@#a, region@#zzz"), getClass().getClassLoader()));
        A11yReport report = new A11yReport("login", "chrome", "u", List.of(
                new Violation("color-contrast", "serious", "Contrast", "u", 1, List.of("#a")),
                new Violation("image-alt", "critical", "Alt text", "u", 2, List.of("img", "img"))));
        assertThat(gate.known(report)).extracting(Violation::rule).containsExactly("color-contrast");
        assertThat(gate.breaches(report)).containsExactly("1 critical (max 0): image-alt (2 elements: img img)");
        A11yReport more = new A11yReport("login", "chrome", "u", List.of(
                new Violation("color-contrast", "serious", "Contrast", "u", 2, List.of("#a", "#new"))));
        assertThat(gate.known(more)).isEmpty();
        assertThat(gate.breaches(more)).containsExactly("1 serious (max 0): color-contrast (2 elements: #a #new)");
    }

    @Test
    void aKnownClassMatchesTheStructuralSelectorsAxeUsesForSeveralElements() {
        A11yGate gate = A11yGate.from(HarnessConfig.load(Map.of(),
                props("harness.a11y.known-issues", "color-contrast@.tone-warn"), getClass().getClassLoader()));
        A11yReport one = new A11yReport("p", "chrome", "u", List.of(
                new Violation("color-contrast", "serious", "Contrast", "u", 1, List.of(".tone-warn"))));
        A11yReport several = new A11yReport("p", "chrome", "u", List.of(new Violation("color-contrast", "serious",
                "Contrast", "u", 2, List.of("tr:nth-child(1) > td:nth-child(1) > .tone-warn.pill",
                        "tr:nth-child(2) > td:nth-child(1) > .tone-warn.pill"))));
        assertThat(gate.breaches(one)).isEmpty();
        assertThat(gate.breaches(several)).isEmpty();
        assertThat(gate.known(several)).hasSize(1);
    }

    @Test
    void aKnownSelectorNeverCoversAnotherElementOrAnUnlistedOne() {
        A11yGate gate = A11yGate.from(HarnessConfig.load(Map.of(),
                props("harness.a11y.known-issues", "color-contrast@.tone-warn"), getClass().getClassLoader()));
        A11yReport parentOnly = new A11yReport("p", "chrome", "u", List.of(new Violation("color-contrast", "serious",
                "Contrast", "u", 1, List.of(".tone-warn > span"))));
        A11yReport otherRule = new A11yReport("p", "chrome", "u", List.of(
                new Violation("link-name", "serious", "Links", "u", 1, List.of(".tone-warn"))));
        A11yReport untargeted = new A11yReport("p", "chrome", "u", List.of(
                new Violation("color-contrast", "serious", "Contrast", "u", 2, List.of(".tone-warn"))));
        assertThat(gate.breaches(parentOnly)).hasSize(1);
        assertThat(gate.breaches(otherRule)).hasSize(1);
        assertThat(gate.breaches(untargeted)).hasSize(1);
    }

    @Test
    void aKnownChainMatchesThroughAttributeSelectorsWithSpacesButNotElsewhere() {
        A11yGate gate = A11yGate.from(HarnessConfig.load(Map.of(),
                props("harness.a11y.known-issues", "color-contrast@.hull-label .code.muted"), getClass().getClassLoader()));
        A11yReport onTheHull = new A11yReport("p", "chrome", "u", List.of(new Violation("color-contrast", "serious",
                "Contrast", "u", 1, List.of("div[title=\"Eastern Star: 58 of 58 moves\"] > .hull-label > .code.muted"))));
        A11yReport elsewhere = new A11yReport("p", "chrome", "u", List.of(new Violation("color-contrast", "serious",
                "Contrast", "u", 1, List.of(".panel > .code.muted"))));
        assertThat(gate.breaches(onTheHull)).isEmpty();
        assertThat(gate.breaches(elsewhere)).hasSize(1);
    }

    private static Properties props(String k, String v) {
        Properties p = new Properties();
        p.setProperty(k, v);
        return p;
    }
}
