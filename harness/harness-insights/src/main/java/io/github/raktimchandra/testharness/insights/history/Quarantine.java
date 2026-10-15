package io.github.raktimchandra.testharness.insights.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The quarantine list: tests whose history says they are flaky. Quarantined tests still run —
 * in a separate, non-blocking job — so their history keeps accumulating and they leave
 * quarantine automatically once they are stable again.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Quarantine(Instant generated, Flakiness.Policy policy, List<Entry> tests) {

    public record Entry(String key, double score, int runs, int flips, Map<String, Long> failureCauses,
                        String reason) {}

    public static Quarantine empty() {
        return new Quarantine(null, Flakiness.Policy.DEFAULT, List.of());
    }

    /**
     * Computes the quarantine from history. A test (key) is quarantined if any of its cases is
     * flaky under the policy; the entry carries the worst case's score.
     */
    public static Quarantine from(List<TestResult> history, Flakiness.Policy policy, Instant now) {
        Map<String, Flakiness.Score> worst = new LinkedHashMap<>();
        for (Flakiness.Score s : Flakiness.scoreAll(history)) {
            if (s.flaky(policy)) {
                worst.merge(s.key(), s, (a, b) -> a.score() >= b.score() ? a : b);
            }
        }
        List<Entry> entries = worst.values().stream()
                .sorted(Comparator.comparingDouble(Flakiness.Score::score).reversed()
                        .thenComparing(Flakiness.Score::key))
                .map(s -> new Entry(s.key(), Math.round(s.score() * 1000) / 1000.0, s.runs(), s.flips(),
                        s.failureCauses(), String.format("%d flips in %d same-commit transitions; failures: %s",
                                s.flips(), s.transitions(), s.failureCauses())))
                .toList();
        return new Quarantine(now, policy, entries);
    }

    public Set<String> keys() {
        return tests.stream().map(Entry::key).collect(Collectors.toUnmodifiableSet());
    }

    public static Quarantine read(Path file) {
        if (!Files.exists(file)) {
            return empty();
        }
        try {
            return HistoryJson.MAPPER.readValue(file.toFile(), Quarantine.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read quarantine file " + file, e);
        }
    }

    public void write(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            HistoryJson.MAPPER.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), this);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write quarantine file " + file, e);
        }
    }
}
