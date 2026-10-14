package io.github.raktimchandra.testharness.ui.a11y;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The accessibility violations found on one page state, in one browser.
 *
 * @param page       logical page/state name, e.g. {@code driver-validation-error}
 * @param browser    browser id
 * @param url        URL scanned
 * @param violations one entry per failing axe rule
 */
public record A11yReport(String page, String browser, String url, List<Violation> violations) {

    /** axe impact levels, most severe first. */
    public static final List<String> IMPACTS = List.of("critical", "serious", "moderate", "minor");

    /**
     * @param rule    axe rule id, e.g. {@code color-contrast}
     * @param impact  critical / serious / moderate / minor
     * @param help    one-line description
     * @param helpUrl Deque University link
     * @param nodes   number of offending elements
     * @param targets CSS selectors of up to five offending elements
     */
    public record Violation(String rule, String impact, String help, String helpUrl, int nodes, List<String> targets) {}

    /** Count of violated rules per impact (all four impacts present, zero when none). */
    public Map<String, Integer> countsByImpact() {
        Map<String, Integer> m = new TreeMap<>((a, b) -> Integer.compare(IMPACTS.indexOf(a), IMPACTS.indexOf(b)));
        IMPACTS.forEach(i -> m.put(i, 0));
        for (Violation v : violations) {
            m.merge(v.impact() == null ? "minor" : v.impact(), 1, Integer::sum);
        }
        return m;
    }
}
