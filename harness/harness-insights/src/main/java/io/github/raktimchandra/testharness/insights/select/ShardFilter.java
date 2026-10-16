package io.github.raktimchandra.testharness.insights.select;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.TestKeys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.PostDiscoveryFilter;

/**
 * Splits a suite across CI machines: with {@code harness.shard=k/n} only the tests of shard k
 * (1-based) of n run. Shards are by test, not class — tests here own their data, so any split
 * is safe. With {@code harness.shard.durations} pointing at a durations file (written by
 * {@code th history durations}), known tests are balanced by recorded duration
 * ({@link ShardPlan}); otherwise, and for tests the file does not know, a stable hash of the
 * test key decides.
 */
public class ShardFilter implements PostDiscoveryFilter {

    private final Optional<int[]> shard;
    private final Optional<ShardPlan> plan;

    public ShardFilter() {
        this(HarnessConfig.get().find("shard").map(ShardFilter::parse),
                HarnessConfig.get().find("shard.durations").flatMap(RepoFiles::locate).map(ShardPlan::read));
    }

    ShardFilter(Optional<int[]> shard) {
        this(shard, Optional.empty());
    }

    ShardFilter(Optional<int[]> shard, Optional<Map<String, Long>> durations) {
        this.shard = shard;
        this.plan = shard.flatMap(s -> durations.map(d -> ShardPlan.of(d, s[1])));
    }

    static int[] parse(String spec) {
        String[] parts = spec.strip().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("harness.shard must look like 2/4, got: " + spec);
        }
        int k = Integer.parseInt(parts[0].strip());
        int n = Integer.parseInt(parts[1].strip());
        if (n < 1 || k < 1 || k > n) {
            throw new IllegalArgumentException("harness.shard needs 1 <= k <= n, got: " + spec);
        }
        return new int[] {k, n};
    }

    /** Bucket (1-based) of a key among n shards; stable across JVMs and machines. */
    public static int bucket(String key, int n) {
        CRC32 crc = new CRC32();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % n) + 1;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (shard.isEmpty() || !Decidable.is(descriptor)) {
            return FilterResult.included("no sharding or container");
        }
        int k = shard.get()[0];
        int n = shard.get()[1];
        String key = TestKeys.of(descriptor.getSource()).orElse(descriptor.getUniqueId().toString());
        int assigned = plan.map(p -> p.shardOf(key)).orElseGet(() -> bucket(key, n));
        return FilterResult.includedIf(assigned == k, () -> "shard " + k + "/" + n, () -> "other shard");
    }
}
