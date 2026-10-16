package io.github.raktimchandra.testharness.insights.tia;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.Launch;
import io.github.raktimchandra.testharness.insights.fixtures.CoverageFixture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real JaCoCo agent. Its own class on purpose: the recorder resets the agent per
 * test, which also clears the coverage of every test that ran before in the same JVM (the
 * module runs one JVM per test class, see the pom), so it must not share a JVM with the
 * selector and mapper tests.
 */
class TiaRecorderTest {

    @TempDir
    Path repo;

    @AfterEach
    void reset() {
        System.clearProperty("harness.tia.record");
        System.clearProperty("harness.tia.dir");
        System.clearProperty("harness.history.suite");
        HarnessConfig.reset();
    }

    @Test
    void recordsRealPerTestCoverageThroughTheJacocoAgent() throws IOException {
        Path dir = repo.resolve("tia");
        System.setProperty("harness.tia.record", "true");
        System.setProperty("harness.tia.dir", dir.toString());
        System.setProperty("harness.history.suite", "fixture-suite");
        HarnessConfig.reset();

        Launch.run(CoverageFixture.class, new TiaRecorder());

        Path rec = dir.resolve("fixture-suite");
        assertThat(rec.resolve("index.jsonl")).exists();
        Path moduleRoot = Path.of("").toAbsolutePath();
        CoverageMap map = new TiaMapper(moduleRoot, List.of("src/test/java")).map(List.of(rec), "local");
        String calc = "src/test/java/io/github/raktimchandra/testharness/insights/fixtures/Calculator.java";
        String fixture = "src/test/java/io/github/raktimchandra/testharness/insights/fixtures/CoverageFixture.java";
        assertThat(map.tests().get(CoverageFixture.class.getName() + "#adds").files()).contains(calc, fixture);
        assertThat(map.tests().get(CoverageFixture.class.getName() + "#touchesNothingOfOurs").files())
                .doesNotContain(calc);
    }
}
