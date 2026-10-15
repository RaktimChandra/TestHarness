package io.github.raktimchandra.testharness.insights.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.Launch;
import io.github.raktimchandra.testharness.insights.fixtures.OutcomesFixture;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryListenerTest {

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        System.clearProperty("harness.history.dir");
        System.clearProperty("harness.history.suite");
        System.clearProperty("harness.history.commit");
        HarnessConfig.reset();
    }

    @Test
    void recordsEveryOutcomeWithItsCause() {
        System.setProperty("harness.history.dir", dir.toString());
        System.setProperty("harness.history.suite", "fixture-suite");
        System.setProperty("harness.history.commit", "c0ffee");
        HarnessConfig.reset();

        Launch.run(OutcomesFixture.class, new HistoryListener());

        List<TestResult> results = HistoryJson.read(dir);
        Map<String, TestResult> byName = results.stream()
                .collect(Collectors.toMap(TestResult::displayName, r -> r));
        String k = OutcomesFixture.class.getName() + "#";
        assertThat(byName.get("passes()").status()).isEqualTo(TestResult.Status.PASSED);
        assertThat(byName.get("passes()").key()).isEqualTo(k + "passes");
        assertThat(byName.get("failsAnAssertion()").failureCause()).isEqualTo("ASSERTION");
        assertThat(byName.get("failsToFindAnElement()").failureCause()).isEqualTo("LOCATOR");
        assertThat(byName.get("failsToFindAnElement()").failureMessage()).contains("book-submit");
        assertThat(byName.get("isAborted()").status()).isEqualTo(TestResult.Status.SKIPPED);
        assertThat(byName.get("isDisabled()").status()).isEqualTo(TestResult.Status.SKIPPED);
        assertThat(results).filteredOn(r -> r.key().equals(k + "parameterised")).hasSize(2)
                .extracting(TestResult::displayName).containsExactlyInAnyOrder("[1] n=1", "[2] n=2");
        assertThat(results).allSatisfy(r -> {
            assertThat(r.commitSha()).isEqualTo("c0ffee");
            assertThat(r.suite()).isEqualTo("fixture-suite");
        });
    }
}
