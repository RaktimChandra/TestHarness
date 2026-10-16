package io.github.raktimchandra.testharness.insights.select;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Duration-balanced sharding. Every shard computes the same plan from the same durations file,
 * so the shards never overlap and need no coordinator: known tests are assigned longest first
 * to the currently lightest shard (LPT scheduling, ties by key then by lowest shard); tests not
 * in the file (new ones) fall back to the stable hash of {@link ShardFilter#bucket}.
 *
 * <p>Durations are the mean, over recorded runs, of a test key's total time in that run
 * (a parameterised method's invocations add up, because the filter shards by method).
 */
public final class ShardPlan {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final int shards;
    private final Map<String, Integer> assignment;
    private final long[] load;

    private ShardPlan(int shards, Map<String, Integer> assignment, long[] load) {
        this.shards = shards;
        this.assignment = assignment;
        this.load = load;
    }

    public static ShardPlan of(Map<String, Long> durationsMs, int shards) {
        if (shards < 1) {
            throw new IllegalArgumentException("shards must be >= 1");
        }
        List<Map.Entry<String, Long>> tests = new ArrayList<>(durationsMs.entrySet());
        tests.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        long[] load = new long[shards];
        Map<String, Integer> assignment = new HashMap<>();
        for (Map.Entry<String, Long> t : tests) {
            int lightest = 0;
            for (int s = 1; s < shards; s++) {
                if (load[s] < load[lightest]) {
                    lightest = s;
                }
            }
            load[lightest] += Math.max(0, t.getValue());
            assignment.put(t.getKey(), lightest + 1);
        }
        return new ShardPlan(shards, assignment, load);
    }

    /** Shard (1-based) for a key: the plan's choice if known, else the stable hash. */
    public int shardOf(String key) {
        Integer s = assignment.get(key);
        return s != null ? s : ShardFilter.bucket(key, shards);
    }

    public boolean knows(String key) {
        return assignment.containsKey(key);
    }

    /** Planned milliseconds per shard (known tests only). */
    public long[] plannedLoad() {
        return load.clone();
    }

    /** Mean per-run total duration of each test key, from recorded history (skipped runs ignored). */
    public static Map<String, Long> durationsFrom(List<TestResult> history) {
        Map<String, Map<String, Long>> perKeyPerRun = new TreeMap<>();
        for (TestResult r : history) {
            if (r.status() == TestResult.Status.SKIPPED) {
                continue;
            }
            perKeyPerRun.computeIfAbsent(r.key(), k -> new HashMap<>()).merge(r.runId(), r.durationMs(), Long::sum);
        }
        Map<String, Long> out = new TreeMap<>();
        perKeyPerRun.forEach((key, runs) -> out.put(key,
                Math.round(runs.values().stream().mapToLong(Long::longValue).average().orElse(0))));
        return out;
    }

    public static Map<String, Long> read(Path file) {
        try {
            return JSON.readValue(file.toFile(), new TypeReference<TreeMap<String, Long>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read shard durations " + file + ": " + e.getMessage(), e);
        }
    }

    public static void write(Map<String, Long> durations, Path file) {
        try {
            if (file.toAbsolutePath().getParent() != null) {
                Files.createDirectories(file.toAbsolutePath().getParent());
            }
            JSON.writeValue(file.toFile(), new TreeMap<>(durations));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return "ShardPlan{" + shards + " shards, " + assignment.size() + " known tests, load ms="
                + java.util.Arrays.stream(load).mapToObj(Long::toString).collect(Collectors.joining("/")) + "}";
    }
}
