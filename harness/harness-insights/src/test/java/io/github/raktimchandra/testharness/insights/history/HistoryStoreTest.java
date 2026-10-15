package io.github.raktimchandra.testharness.insights.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class HistoryStoreTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    Path tmp;

    @Test
    void storesReadsAndIgnoresRepeatedIngestion() {
        Instant t = Instant.parse("2026-10-15T10:00:00Z");
        List<TestResult> run = FlakinessTest.runs("Store#case", "abc123", "PFP", t);
        try (HistoryStore store = HistoryStore.open(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            assertThat(store.insert(run)).isEqualTo(3);
            assertThat(store.insert(run)).as("re-ingesting the same run adds nothing").isZero();
            List<TestResult> back = store.forKey("Store#case");
            assertThat(back).hasSize(3);
            assertThat(back.get(1).status()).isEqualTo(TestResult.Status.FAILED);
            assertThat(back.get(1).failureCause()).isEqualTo("TIMEOUT");
            assertThat(back.get(0).startedAt()).isEqualTo(t.truncatedTo(ChronoUnit.MICROS));
        }
    }

    @Test
    void jsonLinesRoundTripIntoTheDatabase() {
        Path file = tmp.resolve("h/run.jsonl");
        HistoryJson.append(file, FlakinessTest.runs("Json#case", "def456", "PP", Instant.EPOCH));
        HistoryJson.append(file, FlakinessTest.runs("Json#other", "def456", "F", Instant.EPOCH));
        List<TestResult> read = HistoryJson.read(tmp.resolve("h"));
        assertThat(read).hasSize(3);
        try (HistoryStore store = HistoryStore.open(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            store.insert(read);
            assertThat(store.all()).extracting(TestResult::key).contains("Json#case", "Json#other");
        }
    }
}
