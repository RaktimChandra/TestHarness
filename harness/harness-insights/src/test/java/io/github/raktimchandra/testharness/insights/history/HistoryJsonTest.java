package io.github.raktimchandra.testharness.insights.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryJsonTest {

    @TempDir
    Path repo;

    @Test
    void aDirectoryIsSearchedForHistoryFilesOnlyNotOtherJsonLines() throws Exception {
        TestResult r = new TestResult("r1", "c", "main", "api", "A#b", "b()", TestResult.Status.PASSED, 5, null, null,
                null, "local/chrome", Instant.EPOCH);
        HistoryJson.append(repo.resolve("suites/api/target/test-history/run.jsonl"), List.of(r));
        HistoryJson.append(repo.resolve("experiments/demo/history.jsonl"), List.of(r));
        Path healing = repo.resolve("suites/ui/target/healing/healing.jsonl");
        Files.createDirectories(healing.getParent());
        Files.writeString(healing, "{\"test\":\"T\",\"rank\":1}\n");

        assertThat(HistoryJson.read(repo)).hasSize(2).allMatch(t -> t.key().equals("A#b"));
    }
}
