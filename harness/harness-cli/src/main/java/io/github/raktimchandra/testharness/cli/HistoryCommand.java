package io.github.raktimchandra.testharness.cli;

import io.github.raktimchandra.testharness.insights.history.Flakiness;
import io.github.raktimchandra.testharness.insights.history.HistoryJson;
import io.github.raktimchandra.testharness.insights.history.HistoryStore;
import io.github.raktimchandra.testharness.insights.history.Quarantine;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "history", mixinStandardHelpOptions = true, description = "Test run history: ingest, flakiness, quarantine.",
        subcommands = {HistoryCommand.Ingest.class, HistoryCommand.Flaky.class, HistoryCommand.QuarantineCmd.class})
class HistoryCommand {

    @Command(name = "ingest", mixinStandardHelpOptions = true, description = "Load JSON Lines history (target/test-history) into PostgreSQL.")
    static class Ingest implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Option(names = "--jdbc-url", required = true)
        String jdbcUrl;

        @Option(names = "--db-user", defaultValue = "harness")
        String dbUser;

        @Parameters(arity = "1..*", description = ".jsonl files or directories")
        List<Path> sources = new ArrayList<>();

        @Override
        public Integer call() {
            List<TestResult> results = new ArrayList<>();
            sources.forEach(s -> results.addAll(HistoryJson.read(s)));
            String pw = System.getenv().getOrDefault("HARNESS_HISTORY_DB_PASSWORD", "harness");
            try (HistoryStore store = HistoryStore.open(jdbcUrl, dbUser, pw)) {
                int added = store.insert(results);
                spec.commandLine().getOut().printf("Read %d results, %d new%n", results.size(), added);
            }
            return 0;
        }
    }

    @Command(name = "flaky", mixinStandardHelpOptions = true, description = "Flakiness leaderboard (same-commit pass/fail flips).")
    static class Flaky implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Mixin
        HistorySource source;

        @Option(names = "--top", defaultValue = "20")
        int top;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            List<Flakiness.Score> scores = Flakiness.scoreAll(source.load());
            out.printf("%-6s %-5s %-5s %-6s %-6s  %s%n", "score", "runs", "flips", "pass%", "meanms", "test");
            scores.stream().limit(top).forEach(s -> out.printf("%-6.3f %-5d %-5d %-6.1f %-6d  %s [%s] %s%n",
                    s.score(), s.runs(), s.flips(), 100 * s.passRate(), s.meanDurationMs(), s.key(),
                    s.displayName(), s.failureCauses().isEmpty() ? "" : s.failureCauses()));
            return 0;
        }
    }

    @Command(name = "quarantine", mixinStandardHelpOptions = true, description = "Compute the quarantine from history and write quarantine.json.")
    static class QuarantineCmd implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Mixin
        HistorySource source;

        @Option(names = "--write", description = "file to write (default: print only)")
        Path write;

        @Option(names = "--min-runs", defaultValue = "5")
        int minRuns;

        @Option(names = "--min-score", defaultValue = "0.2")
        double minScore;

        @Override
        public Integer call() {
            Quarantine q = Quarantine.from(source.load(), new Flakiness.Policy(minRuns, minScore), Instant.now());
            PrintWriter out = spec.commandLine().getOut();
            out.printf("%d test(s) quarantined (min runs %d, min score %.2f)%n", q.tests().size(), minRuns, minScore);
            q.tests().forEach(e -> out.printf("  %.3f  %s — %s%n", e.score(), e.key(), e.reason()));
            if (write != null) {
                q.write(write);
                out.println("Written to " + write);
            }
            return 0;
        }
    }
}
