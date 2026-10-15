package io.github.raktimchandra.testharness.insights.tia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which repository source files each test executed.
 *
 * @param tests      test key → what it touched and how long it took
 * @param generated  ISO timestamp of the mapping run
 * @param commit     commit the map was recorded on
 */
public record CoverageMap(String generated, String commit, SortedMap<String, TestCoverage> tests) {

    /** @param suite module the test lives in (api-suite, ui-suite, bdd-suite) */
    public record TestCoverage(String suite, SortedSet<String> files, long durationMs) {}

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Inverted view: source file → tests that execute it. */
    public SortedMap<String, SortedSet<String>> testsByFile() {
        SortedMap<String, SortedSet<String>> out = new TreeMap<>();
        tests.forEach((key, cov) -> cov.files().forEach(f -> out.computeIfAbsent(f, x -> new TreeSet<>()).add(key)));
        return out;
    }

    public Set<String> suites() {
        Set<String> s = new TreeSet<>();
        tests.values().forEach(c -> s.add(c.suite()));
        return s;
    }

    public long totalDurationMs() {
        return tests.values().stream().mapToLong(TestCoverage::durationMs).sum();
    }

    public void write(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            JSON.writeValue(file.toFile(), this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CoverageMap read(Path file) {
        try {
            return JSON.readValue(file.toFile(), CoverageMap.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read coverage map " + file, e);
        }
    }

    static CoverageMap of(String generated, String commit, Map<String, TestCoverage> tests) {
        return new CoverageMap(generated, commit, new TreeMap<>(tests));
    }
}
