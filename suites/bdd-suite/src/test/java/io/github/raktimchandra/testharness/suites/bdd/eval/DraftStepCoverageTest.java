package io.github.raktimchandra.testharness.suites.bdd.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class DraftStepCoverageTest {

    private final DraftStepCoverage coverage = new DraftStepCoverage();

    @Test
    void everyStepOfTheSuitesOwnFeaturesMatchesExactlyOneDefinition() throws Exception {
        try (var features = Files.list(Path.of("src/test/resources/features"))) {
            for (Path f : features.toList()) {
                DraftStepCoverage.FileResult r = coverage.check(f);
                assertThat(r.parses()).as(f.toString()).isTrue();
                assertThat(r.unmatched()).as(f + " unmatched").isEmpty();
                assertThat(r.matched()).as(f.toString()).isEqualTo(r.steps());
                assertThat(r.ambiguous()).as(f + " ambiguous").isZero();
            }
        }
    }

    @Test
    void unknownStepsAreReportedEvenAfterTheFirstOne(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("draft.feature");
        Files.writeString(f, """
                @draft
                Feature: draft
                  Scenario: mixed
                    Given a step nobody defined
                    And I am a driver
                    When another undefined step
                    Then the slot has 3 places left
                """);
        DraftStepCoverage.FileResult r = coverage.check(f);
        assertThat(r.steps()).isEqualTo(4);
        assertThat(r.matched()).isEqualTo(2);
        assertThat(r.unmatched()).containsExactly("a step nobody defined", "another undefined step");
    }

    @Test
    void customParameterTypesAndInvalidGherkinAreHandled(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ok.feature"), """
                Feature: f
                  Scenario: s
                    Given a gate slot tomorrow at 09:30 with room for 1 truck
                    Given a gate slot tomorrow at 25:30 with room for 1 truck
                """);
        Files.writeString(dir.resolve("broken.feature"), "Feature: one\n  Scenario: s\n    Given x\nFeature: two\n");
        Map<String, Object> doc = coverage.report(dir, dir.resolve("out/coverage.json"));
        assertThat(doc.get("steps")).isEqualTo(2 + 0);
        assertThat(doc.get("matched")).as("25:30 is not a {time}").isEqualTo(1);
        assertThat(Files.readString(dir.resolve("out/coverage.json"))).contains("\"parses\" : false");
    }

    /** The AI evaluation's entry point: -Dai.drafts=<dir> [-Dai.coverage.out=<file>]. */
    @Test
    @EnabledIfSystemProperty(named = "ai.drafts", matches = ".+")
    void reportOnDrafts() throws Exception {
        Path out = Path.of(System.getProperty("ai.coverage.out", "target/ai-eval/coverage.json"));
        Map<String, Object> doc = coverage.report(Path.of(System.getProperty("ai.drafts")), out);
        LoggerFactory.getLogger(DraftStepCoverageTest.class)
                .info("draft step coverage: {} of {} -> {}", doc.get("matched"), doc.get("steps"), out);
    }
}
