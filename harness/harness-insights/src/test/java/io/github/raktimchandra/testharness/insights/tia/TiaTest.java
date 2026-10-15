package io.github.raktimchandra.testharness.insights.tia;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.Launch;
import io.github.raktimchandra.testharness.insights.fixtures.CoverageFixture;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TiaTest {

    @TempDir
    Path repo;

    @AfterEach
    void reset() {
        System.clearProperty("harness.tia.record");
        System.clearProperty("harness.tia.dir");
        System.clearProperty("harness.history.suite");
        HarnessConfig.reset();
    }

    private void exec(Path file, String... hitClasses) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            ExecutionDataWriter w = new ExecutionDataWriter(out);
            long id = 1;
            for (String c : hitClasses) {
                w.visitClassExecution(new ExecutionData(id++, c, new boolean[] {true, false}));
            }
            w.visitClassExecution(new ExecutionData(id, "com/lib/Unhit", new boolean[] {false}));
        }
    }

    private void source(String path) throws IOException {
        Path p = repo.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "class X {}");
    }

    @Test
    void mapsTestsToTheSourceFilesTheyExecuted() throws IOException {
        source("sut/a/src/main/java/com/acme/Booking.java");
        source("sut/b/src/main/java/com/acme/Rules.java");
        Path rec = repo.resolve("rec/api-suite");
        exec(rec.resolve("1.exec"), "com/acme/Booking", "com/acme/Booking$Inner$1", "org/springframework/X");
        exec(rec.resolve("2.exec"), "com/acme/Rules");
        exec(rec.resolve("3.exec"), "com/acme/Rules");
        Files.writeString(rec.resolve("index.jsonl"), """
                {"exec":"1.exec","key":"T#books","displayName":"books()","status":"SUCCESSFUL","durationMs":100}
                {"exec":"2.exec","key":"T#rules","displayName":"[1]","status":"SUCCESSFUL","durationMs":10}
                {"exec":"3.exec","key":"T#rules","displayName":"[2]","status":"SUCCESSFUL","durationMs":15}
                """);
        CoverageMap map = new TiaMapper(repo, List.of("sut/a/src/main/java", "sut/b/src/main/java"))
                .map(List.of(rec), "abc");
        assertThat(map.tests().get("T#books").files()).containsExactly("sut/a/src/main/java/com/acme/Booking.java");
        assertThat(map.tests().get("T#rules").files()).containsExactly("sut/b/src/main/java/com/acme/Rules.java");
        assertThat(map.tests().get("T#rules").durationMs()).isEqualTo(25);
        assertThat(map.testsByFile()).containsOnlyKeys("sut/a/src/main/java/com/acme/Booking.java",
                "sut/b/src/main/java/com/acme/Rules.java");

        Path json = repo.resolve("map.json");
        map.write(json);
        assertThat(CoverageMap.read(json)).isEqualTo(map);
    }

    @Test
    void selectsByChangeAndExplainsEveryDecision() {
        CoverageMap map = CoverageMap.of("now", "abc", Map.of(
                "Api#a", new CoverageMap.TestCoverage("api-suite", new TreeSet<>(Set.of("sut/x/src/main/java/A.java")), 100),
                "Api#b", new CoverageMap.TestCoverage("api-suite", new TreeSet<>(Set.of("sut/x/src/main/java/B.java")), 200),
                "Ui#c", new CoverageMap.TestCoverage("ui-suite", new TreeSet<>(Set.of("sut/x/src/main/java/A.java")), 400)));
        TiaSelector selector = new TiaSelector(map, Set.of("sut/x/src/main/java/"));

        TiaSelector.Selection a = selector.select(List.of("sut/x/src/main/java/A.java", "README.md"));
        assertThat(a.tests()).containsExactly("Api#a", "Ui#c");
        assertThat(a.runAll()).isFalse();
        assertThat(a.selectedDurationMs()).isEqualTo(500);
        assertThat(a.fullDurationMs()).isEqualTo(700);
        assertThat(a.reasons()).containsEntry("README.md", "documentation: no tests");

        TiaSelector.Selection untested = selector.select(List.of("sut/x/src/main/java/Dead.java"));
        assertThat(untested.tests()).isEmpty();
        assertThat(untested.untestedChanges()).containsExactly("sut/x/src/main/java/Dead.java");

        assertThat(selector.select(List.of("sut/gate-ui/src/pages/DriverPage.jsx")).tests()).containsExactly("Ui#c");
        assertThat(selector.select(List.of("pom.xml")).runAll()).isTrue();
        assertThat(selector.select(List.of("pom.xml")).tests()).hasSize(3);
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
