package io.github.raktimchandra.testharness.insights.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Against PostgreSQL in Testcontainers, or - where Linux containers are unavailable (the
 * Windows CI job) - against the database named by {@code -Dharness.db.url}. Keys are unique
 * per run so a persistent database does not affect the counts.
 */
class HistoryStoreTest {

    private static PostgreSQLContainer<?> container;
    private static String url;
    private static String user;
    private static String password;
    private static final String RUN = Long.toString(System.nanoTime());

    @BeforeAll
    static void database() {
        url = System.getProperty("harness.db.url");
        if (url != null) {
            user = System.getProperty("harness.db.user", "gate");
            password = System.getenv().getOrDefault("HARNESS_DB_PASSWORD", "gate");
        } else {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @TempDir
    Path tmp;

    @Test
    void storesReadsAndIgnoresRepeatedIngestion() {
        Instant t = Instant.parse("2026-10-15T10:00:00Z");
        List<TestResult> run = FlakinessTest.runs("Store#case" + RUN, "abc123" + RUN, "PFP", t);
        try (HistoryStore store = HistoryStore.open(url, user, password)) {
            assertThat(store.insert(run)).isEqualTo(3);
            assertThat(store.insert(run)).as("re-ingesting the same run adds nothing").isZero();
            List<TestResult> back = store.forKey("Store#case" + RUN);
            assertThat(back).hasSize(3);
            assertThat(back.get(1).status()).isEqualTo(TestResult.Status.FAILED);
            assertThat(back.get(1).failureCause()).isEqualTo("TIMEOUT");
            assertThat(back.get(0).startedAt()).isEqualTo(t.truncatedTo(ChronoUnit.MICROS));
        }
    }

    @Test
    void jsonLinesRoundTripIntoTheDatabase() {
        Path file = tmp.resolve("h/run.jsonl");
        HistoryJson.append(file, FlakinessTest.runs("Json#case" + RUN, "def456" + RUN, "PP", Instant.EPOCH));
        HistoryJson.append(file, FlakinessTest.runs("Json#other" + RUN, "def456" + RUN, "F", Instant.EPOCH));
        List<TestResult> read = HistoryJson.read(tmp.resolve("h"));
        assertThat(read).hasSize(3);
        try (HistoryStore store = HistoryStore.open(url, user, password)) {
            store.insert(read);
            assertThat(store.all()).extracting(TestResult::key).contains("Json#case" + RUN, "Json#other" + RUN);
        }
    }
}
