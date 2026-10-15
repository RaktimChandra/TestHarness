package io.github.raktimchandra.testharness.insights.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Flakiness from history: how often a test's outcome flips between consecutive runs <b>of the
 * same commit</b>. Same code, same test, different result is the definition of flaky; a
 * pass→fail change across commits is usually a real regression and is deliberately not counted.
 *
 * <p>score = flips / transitions, where transitions = Σ over commits (runs on that commit − 1),
 * counting only passed/failed runs. 0 = perfectly stable, 1 = alternates every run.
 */
public final class Flakiness {

    /**
     * @param minRuns       runs on repeated commits needed before a test can be judged
     * @param minScore      score at or above which a test is quarantined
     */
    public record Policy(int minRuns, double minScore) {
        public static final Policy DEFAULT = new Policy(5, 0.2);
    }

    /** Score for one test case (key + display name). */
    public record Score(String key, String displayName, int runs, int transitions, int flips, int passes,
                        int failures, double score, Map<String, Long> failureCauses, long meanDurationMs) {

        public double passRate() {
            return runs == 0 ? 0 : (double) passes / runs;
        }

        public boolean flaky(Policy policy) {
            return transitions >= policy.minRuns() - 1 && runs >= policy.minRuns() && score >= policy.minScore()
                    && passes > 0 && failures > 0;
        }
    }

    private Flakiness() {}

    /** Scores every test case in the given history, most flaky first. */
    public static List<Score> scoreAll(List<TestResult> history) {
        Map<String, List<TestResult>> byCase = history.stream()
                .collect(Collectors.groupingBy(TestResult::caseId, LinkedHashMap::new, Collectors.toList()));
        List<Score> scores = new ArrayList<>();
        byCase.values().forEach(rs -> scores.add(score(rs)));
        scores.sort(Comparator.comparingDouble(Score::score).reversed().thenComparing(Score::key));
        return scores;
    }

    public static Score score(List<TestResult> runsOfOneCase) {
        List<TestResult> decided = runsOfOneCase.stream()
                .filter(r -> r.status() != TestResult.Status.SKIPPED)
                .sorted(Comparator.comparing(TestResult::startedAt))
                .toList();
        Map<String, List<TestResult>> byCommit = decided.stream()
                .collect(Collectors.groupingBy(TestResult::commitSha, TreeMap::new, Collectors.toList()));
        int transitions = 0;
        int flips = 0;
        for (List<TestResult> runs : byCommit.values()) {
            for (int i = 1; i < runs.size(); i++) {
                transitions++;
                if (runs.get(i).status() != runs.get(i - 1).status()) {
                    flips++;
                }
            }
        }
        int passes = (int) decided.stream().filter(r -> r.status() == TestResult.Status.PASSED).count();
        Map<String, Long> causes = decided.stream()
                .filter(r -> r.status() == TestResult.Status.FAILED)
                .collect(Collectors.groupingBy(r -> r.failureCause() == null ? "UNKNOWN" : r.failureCause(),
                        TreeMap::new, Collectors.counting()));
        long mean = decided.isEmpty() ? 0 : Math.round(decided.stream().mapToLong(TestResult::durationMs).average()
                .orElse(0));
        TestResult first = runsOfOneCase.get(0);
        return new Score(first.key(), first.displayName(), decided.size(), transitions, flips, passes,
                decided.size() - passes, transitions == 0 ? 0 : (double) flips / transitions, causes, mean);
    }
}
