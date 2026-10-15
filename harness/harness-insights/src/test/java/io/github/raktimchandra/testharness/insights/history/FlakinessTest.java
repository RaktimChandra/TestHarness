package io.github.raktimchandra.testharness.insights.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlakinessTest {

    static List<TestResult> runs(String key, String commit, String outcomes, Instant start) {
        List<TestResult> out = new ArrayList<>();
        for (int i = 0; i < outcomes.length(); i++) {
            boolean pass = outcomes.charAt(i) == 'P';
            TestResult.Status st = outcomes.charAt(i) == 'S' ? TestResult.Status.SKIPPED
                    : pass ? TestResult.Status.PASSED : TestResult.Status.FAILED;
            out.add(new TestResult("run" + commit + i, commit, "main", "api-suite", key, key, st, 100 + i,
                    st == TestResult.Status.FAILED ? "TIMEOUT" : null, null, null, "ci/chrome",
                    start.plusSeconds(60L * i)));
        }
        return out;
    }

    @Test
    void flipsAreCountedOnlyWithinOneCommit() {
        Instant t = Instant.parse("2026-10-15T00:00:00Z");
        List<TestResult> h = new ArrayList<>(runs("A#a", "c1", "PPPPP", t));
        h.addAll(runs("A#a", "c2", "FFFFF", t.plusSeconds(3600)));
        Flakiness.Score s = Flakiness.score(h);
        assertThat(s.flips()).as("pass on c1, fail on c2 is a regression, not flakiness").isZero();
        assertThat(s.transitions()).isEqualTo(8);
        assertThat(s.score()).isZero();
        assertThat(s.flaky(Flakiness.Policy.DEFAULT)).isFalse();
    }

    @Test
    void alternatingOnTheSameCommitIsFlaky() {
        Flakiness.Score s = Flakiness.score(runs("B#b", "c1", "PFPPFP", Instant.EPOCH));
        assertThat(s.flips()).isEqualTo(4);
        assertThat(s.transitions()).isEqualTo(5);
        assertThat(s.score()).isEqualTo(0.8);
        assertThat(s.passRate()).isEqualTo(4.0 / 6);
        assertThat(s.failureCauses()).containsEntry("TIMEOUT", 2L);
        assertThat(s.flaky(Flakiness.Policy.DEFAULT)).isTrue();
    }

    @Test
    void skippedRunsAndTooFewRunsDoNotCount() {
        Flakiness.Score s = Flakiness.score(runs("C#c", "c1", "PSFSP", Instant.EPOCH));
        assertThat(s.runs()).isEqualTo(3);
        assertThat(s.flips()).isEqualTo(2);
        assertThat(s.flaky(Flakiness.Policy.DEFAULT)).as("3 runs < 5 required").isFalse();
    }

    @Test
    void quarantineHoldsOnlyFlakyTestsMostFlakyFirst() {
        List<TestResult> h = new ArrayList<>();
        h.addAll(runs("Stable#ok", "c1", "PPPPPP", Instant.EPOCH));
        h.addAll(runs("Broken#always", "c1", "FFFFFF", Instant.EPOCH));
        h.addAll(runs("Flaky#mild", "c1", "PPPFPP", Instant.EPOCH));
        h.addAll(runs("Flaky#wild", "c1", "PFPFPF", Instant.EPOCH));
        Quarantine q = Quarantine.from(h, Flakiness.Policy.DEFAULT, Instant.EPOCH);
        assertThat(q.tests()).extracting(Quarantine.Entry::key).containsExactly("Flaky#wild", "Flaky#mild");
        assertThat(q.keys()).doesNotContain("Broken#always").as("always failing is broken, not flaky");
    }
}
