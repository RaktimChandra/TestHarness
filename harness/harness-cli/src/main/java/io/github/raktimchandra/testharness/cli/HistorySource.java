package io.github.raktimchandra.testharness.cli;

import io.github.raktimchandra.testharness.insights.history.HistoryJson;
import io.github.raktimchandra.testharness.insights.history.HistoryStore;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Option;

/** Where history is read from: PostgreSQL, JSON Lines files, or both. */
class HistorySource {

    @Option(names = "--jdbc-url", description = "PostgreSQL URL, e.g. jdbc:postgresql://localhost:5433/history")
    String jdbcUrl;

    @Option(names = "--db-user", defaultValue = "harness")
    String dbUser;

    @Option(names = "--jsonl", description = "a .jsonl file or a directory of them (repeatable)")
    List<Path> jsonl = new ArrayList<>();

    String password() {
        String p = System.getenv("HARNESS_HISTORY_DB_PASSWORD");
        return p == null || p.isBlank() ? "harness" : p;
    }

    List<TestResult> load() {
        List<TestResult> all = new ArrayList<>();
        jsonl.forEach(p -> all.addAll(HistoryJson.read(p)));
        if (jdbcUrl != null) {
            try (HistoryStore store = HistoryStore.open(jdbcUrl, dbUser, password())) {
                all.addAll(store.all());
            }
        }
        if (jdbcUrl == null && jsonl.isEmpty()) {
            throw new IllegalArgumentException("Give --jdbc-url and/or --jsonl");
        }
        return all;
    }
}
