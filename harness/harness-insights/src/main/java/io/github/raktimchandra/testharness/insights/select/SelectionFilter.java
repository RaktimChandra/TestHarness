package io.github.raktimchandra.testharness.insights.select;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.TestKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs only the tests listed in {@code harness.select} (a file with one test key per line;
 * {@code #} comments allowed; a line without {@code #} selects a whole class or feature). It is
 * how re-running failed tests and test impact analysis pick tests, for JUnit and Cucumber alike.
 */
public class SelectionFilter implements PostDiscoveryFilter {

    private static final Logger log = LoggerFactory.getLogger(SelectionFilter.class);

    private final Optional<Set<String>> selected;

    public SelectionFilter() {
        this(HarnessConfig.get().find("harness.select").map(SelectionFilter::readKeys));
    }

    SelectionFilter(Optional<Set<String>> selected) {
        this.selected = selected;
        selected.ifPresent(s -> log.info("Test selection active: {} key(s)", s.size()));
    }

    static Set<String> readKeys(String file) {
        Path path = RepoFiles.locate(file)
                .orElseThrow(() -> new IllegalArgumentException("harness.select file not found: " + file));
        try {
            return Files.readAllLines(path).stream()
                    .map(l -> l.replaceFirst("\\s+#.*$", "").strip())
                    .filter(l -> !l.isEmpty() && !l.startsWith("# "))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (selected.isEmpty() || !Decidable.is(descriptor)) {
            return FilterResult.included("no selection or container");
        }
        Optional<String> key = TestKeys.of(descriptor.getSource());
        boolean in = key.map(k -> selected.get().contains(k) || selected.get().contains(TestKeys.owner(k)))
                .orElse(false);
        return FilterResult.includedIf(in, () -> "selected", () -> "not selected");
    }
}
