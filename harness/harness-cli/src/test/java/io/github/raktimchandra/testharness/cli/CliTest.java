package io.github.raktimchandra.testharness.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.insights.history.HistoryJson;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CliTest {

    @TempDir
    Path tmp;

    private record Out(int code, String out, String err) {}

    private static Out run(String... args) {
        CommandLine cl = HarnessCli.commandLine();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        int code = cl.execute(args);
        return new Out(code, out.toString(), err.toString());
    }

    @Test
    void helpListsEveryCommand() {
        Out o = run("--help");
        assertThat(o.code()).isZero();
        assertThat(o.out()).contains("run", "rerun-failed", "report", "history", "tia", "ai", "dashboard");
    }

    @Test
    void dashboardIsWrittenFromTheRepositoryResults() {
        Path out = tmp.resolve("dash");
        Out o = run("dashboard", "--out", out.toString());
        assertThat(o.code()).as(o.err()).isZero();
        assertThat(o.out()).contains("Dashboard: ");
        assertThat(out.resolve("index.html")).exists();
        assertThat(out.resolve("data.js")).content().startsWith("window.__TESTHARNESS__ = ");
    }

    @Test
    void runDryRunPrintsTheMavenCommand() {
        Out o = run("run", "--suite", "ui", "--browser", "edge", "--tags", "smoke", "--dry-run");
        assertThat(o.code()).isZero();
        assertThat(o.out()).contains("verify -pl suites/ui-suite -DskipPerf=true -Dgroups=smoke -Dharness.ui.browser=edge");
    }

    @Test
    void rerunFailedFindsFailedTestsPerModule() throws Exception {
        Path reports = tmp.resolve("suites/api-suite/target/surefire-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-a.ATest.xml"), """
                <testsuite><testcase classname="a.ATest" name="ok"/>
                <testcase classname="a.ATest" name="bad()"><failure type="x" message="m">s</failure></testcase>
                <testcase classname="a.ATest" name="[1] n=1"><error type="y" message="m">s</error></testcase>
                </testsuite>""");
        assertThat(RerunFailedCommand.failedByModule(tmp)).containsOnlyKeys("suites/api-suite");
        assertThat(RerunFailedCommand.failedByModule(tmp).get("suites/api-suite")).contains("a.ATest#bad");
    }

    @Test
    void flakyLeaderboardAndQuarantineFromJsonLines() {
        List<TestResult> h = new ArrayList<>();
        String[] outcomes = {"P", "F", "P", "F", "P", "P"};
        for (int i = 0; i < outcomes.length; i++) {
            h.add(new TestResult("r" + i, "c1", "main", "api-suite", "X#flaky", "flaky()",
                    outcomes[i].equals("P") ? TestResult.Status.PASSED : TestResult.Status.FAILED, 10, outcomes[i]
                    .equals("P") ? null : "ASSERTION", null, null, "local/chrome", Instant.EPOCH.plusSeconds(i)));
            h.add(new TestResult("r" + i, "c1", "main", "api-suite", "X#stable", "stable()",
                    TestResult.Status.PASSED, 10, null, null, null, "local/chrome", Instant.EPOCH.plusSeconds(i)));
        }
        Path file = tmp.resolve("h.jsonl");
        HistoryJson.append(file, h);
        Out flaky = run("history", "flaky", "--jsonl", file.toString());
        assertThat(flaky.out().lines().skip(1).findFirst().orElse("")).contains("X#flaky").startsWith("0.800");
        Path q = tmp.resolve("quarantine.json");
        Out quarantine = run("history", "quarantine", "--jsonl", file.toString(), "--write", q.toString());
        assertThat(quarantine.out()).contains("1 test(s) quarantined").contains("X#flaky");
        assertThat(q).content().contains("X#flaky").doesNotContain("X#stable");

        Path d = tmp.resolve("shard-durations.json");
        Out durations = run("history", "durations", "--jsonl", file.toString(), "--out", d.toString(), "--shards", "2");
        assertThat(durations.out()).contains("2 tests, 20 ms in total").contains("shard 1/2: 10 ms", "shard 2/2: 10 ms");
        assertThat(d).content().contains("\"X#flaky\" : 10", "\"X#stable\" : 10");
        Path empty = tmp.resolve("empty.jsonl");
        HistoryJson.append(empty, List.of());
        assertThat(run("history", "durations", "--jsonl", empty.toString(), "--out", d.toString()).code()).isEqualTo(1);
    }

    @Test
    void dashDPropertiesAnywhereBecomeSystemProperties() {
        String[] rest = HarnessCli.applySystemProperties(new String[] {"ai", "-Dharness.cli.test=yes", "explain"});
        try {
            assertThat(rest).containsExactly("ai", "explain");
            assertThat(System.getProperty("harness.cli.test")).isEqualTo("yes");
        } finally {
            System.clearProperty("harness.cli.test");
        }
    }

    @Test
    void aiGherkinExplainsThatNoProviderIsConfigured() {
        Out o = run("ai", "gherkin", "--story", "As a driver I want to cancel");
        assertThat(o.code()).isEqualTo(2);
        assertThat(o.err()).contains("No AI provider configured");
    }
}
