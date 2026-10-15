package io.github.raktimchandra.testharness.insights.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.insights.Launch;
import io.github.raktimchandra.testharness.insights.fixtures.OutcomesFixture;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

class FiltersTest {

    private static final String K = OutcomesFixture.class.getName() + "#";

    private static Set<String> methods(PostDiscoveryFilter filter) {
        TestPlan plan = Launch.discover(OutcomesFixture.class, filter);
        return plan.getRoots().stream().flatMap(r -> plan.getDescendants(r).stream())
                .filter(t -> t.isTest() || t.getSource().filter(MethodSource.class::isInstance).isPresent())
                .map(t -> ((MethodSource) t.getSource().orElseThrow()).getMethodName())
                .collect(Collectors.toSet());
    }

    @Test
    void quarantineExcludeRemovesQuarantinedTestsAndOnlyKeepsJustThem() {
        Set<String> q = Set.of(K + "failsAnAssertion", K + "parameterised");
        assertThat(methods(new QuarantineFilter(QuarantineFilter.Mode.EXCLUDE, q)))
                .doesNotContain("failsAnAssertion", "parameterised").contains("passes");
        assertThat(methods(new QuarantineFilter(QuarantineFilter.Mode.ONLY, q)))
                .containsExactlyInAnyOrder("failsAnAssertion", "parameterised");
        assertThat(methods(new QuarantineFilter(QuarantineFilter.Mode.OFF, q))).hasSize(6);
    }

    @Test
    void selectionByMethodOrByWholeClass() {
        assertThat(methods(new SelectionFilter(Optional.of(Set.of(K + "passes")))))
                .containsExactly("passes");
        assertThat(methods(new SelectionFilter(Optional.of(Set.of(OutcomesFixture.class.getName())))))
                .hasSize(6);
        assertThat(methods(new SelectionFilter(Optional.empty()))).hasSize(6);
    }

    @Test
    void shardsPartitionTheSuiteExactly() {
        int n = 3;
        List<Set<String>> shards = IntStream.rangeClosed(1, n)
                .mapToObj(k -> methods(new ShardFilter(Optional.of(new int[] {k, n})))).toList();
        Set<String> union = new HashSet<>();
        int total = 0;
        for (Set<String> s : shards) {
            union.addAll(s);
            total += s.size();
        }
        assertThat(union).hasSize(6);
        assertThat(total).as("no test in two shards").isEqualTo(6);
        assertThat(ShardFilter.bucket("same", 4)).isEqualTo(ShardFilter.bucket("same", 4));
    }

    @Test
    void shardSpecIsValidated() {
        assertThat(ShardFilter.parse("2/4")).containsExactly(2, 4);
        assertThatThrownBy(() -> ShardFilter.parse("5/4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShardFilter.parse("x")).isInstanceOf(IllegalArgumentException.class);
    }
}
