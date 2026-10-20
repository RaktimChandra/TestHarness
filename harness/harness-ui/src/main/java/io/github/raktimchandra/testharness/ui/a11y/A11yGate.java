package io.github.raktimchandra.testharness.ui.a11y;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thresholds per impact ({@code a11y.max-critical}, {@code a11y.max-serious},
 * {@code a11y.max-moderate}, {@code a11y.max-minor}); {@code -1} means "report only".
 * Defaults: no critical or serious violations allowed; moderate and minor are reported.
 *
 * <p>{@code a11y.known-issues} lists violations already reported to the owners of a system the
 * tests do not control, as {@code rule@selector} (comma-separated). A violation whose every
 * element is listed does not count against the thresholds, but is still reported
 * ({@link #known}); any other element, or another rule, still fails.
 *
 * <p>axe names an element by the shortest selector that is unique on the page: {@code .tone-warn}
 * while there is one such element, {@code tr:nth-child(2) > td:nth-child(1) > .tone-warn.pill}
 * once there are several. So a listed selector matches a target that equals it, or whose last
 * compound selector (the element itself) has every class, id and tag of the listed one. A
 * violation counts as known only when axe returned a target for each of its elements.
 */
public record A11yGate(int maxCritical, int maxSerious, int maxModerate, int maxMinor, Set<String> knownIssues) {

    public A11yGate(int maxCritical, int maxSerious, int maxModerate, int maxMinor) {
        this(maxCritical, maxSerious, maxModerate, maxMinor, Set.of());
    }

    public static A11yGate from(HarnessConfig c) {
        Set<String> known = new java.util.TreeSet<>();
        for (String k : c.string("a11y.known-issues", "").split(",")) {
            if (!k.isBlank()) {
                known.add(k.strip());
            }
        }
        return new A11yGate(c.integer("a11y.max-critical", 0), c.integer("a11y.max-serious", 0),
                c.integer("a11y.max-moderate", -1), c.integer("a11y.max-minor", -1), Set.copyOf(known));
    }

    private boolean isKnown(A11yReport.Violation v) {
        return !knownIssues.isEmpty() && v.targets() != null && !v.targets().isEmpty()
                && v.targets().size() == v.nodes()
                && v.targets().stream().allMatch(t -> knownIssues.stream().anyMatch(k -> matches(k, v.rule(), t)));
    }

    static boolean matches(String known, String rule, String target) {
        int at = known.indexOf('@');
        if (at < 0 || !known.substring(0, at).equals(rule)) {
            return false;
        }
        String selector = known.substring(at + 1).strip();
        if (selector.equals(target.strip())) {
            return true;
        }
        if (selector.isEmpty() || selector.matches(".*[\\s>+~].*")) {
            return false; // only a single compound selector is matched loosely
        }
        String[] parts = target.strip().split("\\s*[\\s>+~]\\s*");
        Set<String> element = simpleSelectors(parts[parts.length - 1]);
        Set<String> wanted = simpleSelectors(selector);
        return !wanted.isEmpty() && element.containsAll(wanted);
    }

    /** Tag, {@code #id} and {@code .class} parts of one compound selector; pseudo-classes and attributes dropped. */
    private static Set<String> simpleSelectors(String compound) {
        String plain = compound.replaceAll(":{1,2}[\\w-]+(\\([^)]*\\))?", "").replaceAll("\\[[^\\]]*\\]", "");
        Set<String> out = new java.util.HashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[.#]?[\\w-]+").matcher(plain);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** Violations excluded from the thresholds because they are listed as known issues. */
    public List<A11yReport.Violation> known(A11yReport report) {
        return report.violations().stream().filter(this::isKnown).toList();
    }

    /** Human-readable breaches; empty when the report is within the thresholds. */
    public List<String> breaches(A11yReport report) {
        report = new A11yReport(report.page(), report.browser(), report.url(),
                report.violations().stream().filter(v -> !isKnown(v)).toList());
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
                    .map(v -> v.rule() + " (" + v.nodes() + " element" + (v.nodes() == 1 ? "" : "s")
                            + (v.targets() == null || v.targets().isEmpty() ? ""
                                    : ": " + String.join(" ", v.targets().subList(0, Math.min(3, v.targets().size()))))
                            + ")").toList();
            out.add(count + " " + impact + " (max " + max + "): " + String.join(", ", rules));
        }
    }
}
