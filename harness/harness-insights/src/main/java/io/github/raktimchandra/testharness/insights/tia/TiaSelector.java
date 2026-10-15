package io.github.raktimchandra.testharness.insights.tia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Chooses the tests a change can affect. The rules are conservative: when the map cannot
 * answer (build files, framework code, test code, frontend), everything that could be
 * affected runs, and every decision is explained in {@link Selection#reasons()}.
 *
 * <table>
 *   <caption>Rules, first match wins per changed file</caption>
 *   <tr><td>documentation ({@code *.md}, {@code docs/})</td><td>nothing</td></tr>
 *   <tr><td>SUT Java source in the map</td><td>the tests that executed it</td></tr>
 *   <tr><td>SUT Java source not in the map</td><td>nothing — and flagged as <b>untested</b></td></tr>
 *   <tr><td>frontend ({@code sut/gate-ui/}) other than its unit tests</td><td>every UI and BDD test</td></tr>
 *   <tr><td>anything else (build, framework, suites, resources, migrations)</td><td>everything</td></tr>
 * </table>
 */
public final class TiaSelector {

    public record Selection(SortedSet<String> tests, Map<String, String> reasons, List<String> untestedChanges,
                            boolean runAll, long selectedDurationMs, long fullDurationMs) {}

    private final CoverageMap map;
    private final SortedMap<String, SortedSet<String>> byFile;
    private final Set<String> sutSourceRoots;

    public TiaSelector(CoverageMap map, Set<String> sutSourceRoots) {
        this.map = map;
        this.byFile = map.testsByFile();
        this.sutSourceRoots = sutSourceRoots;
    }

    public Selection select(List<String> changedFiles) {
        SortedSet<String> tests = new TreeSet<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        List<String> untested = new ArrayList<>();
        boolean all = false;
        for (String raw : changedFiles) {
            String f = raw.replace('\\', '/').strip();
            if (f.isEmpty()) {
                continue;
            }
            if (f.endsWith(".md") || f.startsWith("docs/") || f.equals("LICENSE")) {
                reasons.put(f, "documentation: no tests");
            } else if (isSutJava(f)) {
                SortedSet<String> hit = byFile.get(f);
                if (hit == null || hit.isEmpty()) {
                    untested.add(f);
                    reasons.put(f, "SUT source executed by no test in the map: UNTESTED change");
                } else {
                    tests.addAll(hit);
                    reasons.put(f, hit.size() + " test(s) executed this file");
                }
            } else if (f.startsWith("sut/gate-ui/") && !f.contains(".test.")) {
                map.tests().forEach((k, c) -> {
                    if (c.suite().startsWith("ui") || c.suite().startsWith("bdd")) {
                        tests.add(k);
                    }
                });
                reasons.put(f, "frontend change (no per-test JS coverage): all UI and BDD tests");
            } else {
                all = true;
                reasons.put(f, "not mapped (build, framework, test or resource file): all tests");
            }
        }
        if (all) {
            tests.addAll(map.tests().keySet());
        }
        long selectedMs = tests.stream().mapToLong(k -> map.tests().get(k).durationMs()).sum();
        return new Selection(tests, reasons, untested, all, selectedMs, map.totalDurationMs());
    }

    private boolean isSutJava(String f) {
        return f.endsWith(".java") && sutSourceRoots.stream().anyMatch(f::startsWith);
    }
}
