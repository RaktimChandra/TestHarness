package io.github.raktimchandra.testharness.insights.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.insights.history.TestResult;
import io.github.raktimchandra.testharness.insights.history.TestResult.Status;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShardPlanTest {

    private static TestResult result(String run, String key, Status status, long ms) {
        return result(run, "ci/chrome", key, status, ms);
    }

    private static TestResult result(String run, String env, String key, Status status, long ms) {
        return new TestResult(run, "abc", "main", "ui", key, key, status, ms, null, null, null, env, Instant.EPOCH);
    }

    @Test
    void longestTestsGoFirstToTheLightestShard() {
        Map<String, Long> d = new LinkedHashMap<>();
        d.put("a", 100L);
        d.put("b", 60L);
        d.put("c", 50L);
        d.put("d", 40L);
        d.put("e", 10L);
        ShardPlan plan = ShardPlan.of(d, 2);
        // LPT: a→1 (100), b→2 (60), c→2 (110), d→1 (140), e→2 (120)
        assertThat(List.of(plan.shardOf("a"), plan.shardOf("b"), plan.shardOf("c"), plan.shardOf("d"),
                plan.shardOf("e"))).containsExactly(1, 2, 2, 1, 2);
        assertThat(plan.plannedLoad()).containsExactly(140, 120);
    }

    @Test
    void balancesWhereTheHashSplitDoesNot() {
        // Ten tests of very different lengths: the plan's worst shard is within the largest single
        // test of the ideal, which the hash split does not guarantee.
        Map<String, Long> d = new TreeMap<>();
        long[] ms = {9000, 7000, 4000, 3000, 2500, 1200, 900, 600, 300, 100};
        for (int i = 0; i < ms.length; i++) {
            d.put("Suite#t" + i, ms[i]);
        }
        long total = Arrays.stream(ms).sum();
        long[] load = ShardPlan.of(d, 3).plannedLoad();
        assertThat(Arrays.stream(load).sum()).isEqualTo(total);
        assertThat(Arrays.stream(load).max().orElseThrow()).isLessThanOrEqualTo(total / 3 + 9000);
        assertThat(Arrays.stream(load).max().orElseThrow() - Arrays.stream(load).min().orElseThrow())
                .isLessThanOrEqualTo(9000);
    }

    @Test
    void thePlanIsTheSameWhateverOrderTheDurationsArriveIn() {
        Map<String, Long> forward = new LinkedHashMap<>();
        Map<String, Long> backward = new LinkedHashMap<>();
        String[] keys = {"x", "y", "z", "w"};
        for (String k : keys) {
            forward.put(k, 50L); // all equal: only the key order breaks the ties
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            backward.put(keys[i], 50L);
        }
        ShardPlan p1 = ShardPlan.of(forward, 2);
        ShardPlan p2 = ShardPlan.of(backward, 2);
        for (String k : keys) {
            assertThat(p1.shardOf(k)).isEqualTo(p2.shardOf(k));
        }
    }

    @Test
    void unknownTestsFallBackToTheStableHash() {
        ShardPlan plan = ShardPlan.of(Map.of("known", 10L), 4);
        assertThat(plan.knows("new#test")).isFalse();
        assertThat(plan.shardOf("new#test")).isEqualTo(ShardFilter.bucket("new#test", 4));
        assertThatThrownBy(() -> ShardPlan.of(Map.of(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void durationsAreTheMeanPerRunTotalIgnoringSkips() {
        List<TestResult> history = List.of(
                // run 1: a parameterised method with two invocations adds up to 300 ms
                result("r1", "C#p", Status.PASSED, 100), result("r1", "C#p", Status.PASSED, 200),
                // run 2: 500 ms; mean of 300 and 500 is 400
                result("r2", "C#p", Status.FAILED, 500),
                result("r1", "C#s", Status.SKIPPED, 0), result("r2", "C#s", Status.PASSED, 80),
                // the same run id in another browser is another run, not more time in r2
                result("r2", "ci/firefox", "C#s", Status.PASSED, 120));
        assertThat(ShardPlan.durationsFrom(history)).containsExactly(Map.entry("C#p", 400L), Map.entry("C#s", 100L));
    }

    @Test
    void durationsRoundTripThroughTheFile(@TempDir Path dir) {
        Path file = dir.resolve("nested/shard-durations.json");
        ShardPlan.write(Map.of("b", 2L, "a", 1L), file);
        assertThat(ShardPlan.read(file)).containsExactly(Map.entry("a", 1L), Map.entry("b", 2L));
        assertThatThrownBy(() -> ShardPlan.read(dir.resolve("missing.json")))
                .hasMessageContaining("Cannot read shard durations");
    }
}
