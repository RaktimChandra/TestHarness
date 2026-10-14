package io.github.raktimchandra.testharness.ui.a11y;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thresholds per impact ({@code a11y.max-critical}, {@code a11y.max-serious},
 * {@code a11y.max-moderate}, {@code a11y.max-minor}); {@code -1} means "report only".
 * Defaults: no critical or serious violations allowed; moderate and minor are reported.
 */
public record A11yGate(int maxCritical, int maxSerious, int maxModerate, int maxMinor) {

    public static A11yGate from(HarnessConfig c) {
        return new A11yGate(c.integer("a11y.max-critical", 0), c.integer("a11y.max-serious", 0),
                c.integer("a11y.max-moderate", -1), c.integer("a11y.max-minor", -1));
    }

    /** Human-readable breaches; empty when the report is within the thresholds. */
    public List<String> breaches(A11yReport report) {
        Map<String, Integer> counts = report.countsByImpact();
        List<String> out = new ArrayList<>();
        check(out, "critical", counts.get("critical"), maxCritical, report);
        check(out, "serious", counts.get("serious"), maxSerious, report);
        check(out, "moderate", counts.get("moderate"), maxModerate, report);
        check(out, "minor", counts.get("minor"), maxMinor, report);
        return out;
    }

    private static void check(List<String> out, String impact, int count, int max, A11yReport report) {
        if (max >= 0 && count > max) {
            List<String> rules = report.violations().stream().filter(v -> impact.equals(v.impact()))
                    .map(v -> v.rule() + " (" + v.nodes() + " element" + (v.nodes() == 1 ? "" : "s") + ")").toList();
            out.add(count + " " + impact + " (max " + max + "): " + String.join(", ", rules));
        }
    }
}
