package io.github.raktimchandra.testharness.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the Maven invocation for a run; kept pure so every option is unit-tested. */
final class MavenCommand {

    static final Map<String, String> SUITES = new LinkedHashMap<>();

    static {
        SUITES.put("api", "suites/api-suite");
        SUITES.put("ui", "suites/ui-suite");
        SUITES.put("bdd", "suites/bdd-suite");
        SUITES.put("perf", "suites/perf-suite");
    }

    /** Options of {@code harness run}. Null or empty means "not set". */
    record Options(List<String> suites, String tags, String bddTags, String browser, String env, String gridUrl,
                   boolean headed, String shard, String select, String quarantine, String seededBugs,
                   Integer workers, boolean alsoUpstream, List<String> extra) {}

    private MavenCommand() {}

    static List<String> build(String mvnw, Options o) {
        List<String> cmd = new ArrayList<>(List.of(mvnw, "-B", "verify"));
        List<String> modules = new ArrayList<>();
        boolean all = o.suites() == null || o.suites().isEmpty() || o.suites().contains("all");
        if (!all) {
            for (String s : o.suites()) {
                String m = SUITES.get(s);
                if (m == null) {
                    throw new IllegalArgumentException("Unknown suite '" + s + "'; use " + String.join(", ",
                            SUITES.keySet()) + " or all");
                }
                modules.add(m);
            }
            cmd.add("-pl");
            cmd.add(String.join(",", modules));
            if (o.alsoUpstream()) {
                cmd.add("-am");
            }
        }
        if (!all && !modules.contains("suites/perf-suite")) {
            cmd.add("-DskipPerf=true");
        }
        prop(cmd, "groups", o.tags());
        prop(cmd, "cucumber.filter.tags", o.bddTags());
        prop(cmd, "harness.ui.browser", o.browser());
        prop(cmd, "harness.env", o.env());
        prop(cmd, "harness.ui.grid-url", o.gridUrl());
        if (o.headed()) {
            cmd.add("-Dharness.ui.headless=false");
        }
        prop(cmd, "harness.shard", o.shard());
        prop(cmd, "harness.select", o.select());
        prop(cmd, "harness.quarantine.mode", o.quarantine());
        prop(cmd, "harness.sut.seeded-bugs", o.seededBugs());
        if (o.workers() != null) {
            cmd.add("-Djunit.jupiter.execution.parallel.config.fixed.parallelism=" + o.workers());
            cmd.add("-Djunit.jupiter.execution.parallel.config.fixed.max-pool-size=" + o.workers());
        }
        if (o.select() != null && !o.select().isBlank()) {
            cmd.add("-Dsurefire.failIfNoSpecifiedTests=false");
        }
        if (o.extra() != null) {
            cmd.addAll(o.extra());
        }
        return cmd;
    }

    private static void prop(List<String> cmd, String name, String value) {
        if (value != null && !value.isBlank()) {
            cmd.add("-D" + name + "=" + value);
        }
    }
}
