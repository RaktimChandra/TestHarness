package io.github.raktimchandra.testharness.insights.tia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.tools.ExecFileLoader;

/**
 * Turns the recorder's per-test {@code .exec} files into a {@link CoverageMap}: a class counts
 * as executed by a test if any of its probes fired; nested and synthetic classes belong to the
 * source file of their outermost class. Only classes whose source is in one of the given source
 * roots are kept (the SUT), so framework and library code never appears in the map.
 */
public final class TiaMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path repoRoot;
    private final List<Path> sourceRoots;
    private final Map<String, Optional<String>> sourceCache = new HashMap<>();

    /** @param sourceRoots repository-relative source roots, e.g. {@code sut/gate-api/src/main/java} */
    public TiaMapper(Path repoRoot, List<String> sourceRoots) {
        this.repoRoot = repoRoot;
        this.sourceRoots = sourceRoots.stream().map(Path::of).toList();
    }

    /** @param recordingDirs one directory per suite, as written by {@link TiaRecorder} */
    public CoverageMap map(List<Path> recordingDirs, String commit) {
        Map<String, CoverageMap.TestCoverage> tests = new HashMap<>();
        for (Path dir : recordingDirs) {
            String suite = dir.getFileName().toString();
            Path index = dir.resolve("index.jsonl");
            if (!Files.exists(index)) {
                continue;
            }
            try (Stream<String> lines = Files.lines(index, StandardCharsets.UTF_8)) {
                for (String line : lines.filter(l -> !l.isBlank()).toList()) {
                    JsonNode e = JSON.readTree(line);
                    SortedSet<String> files = filesHit(dir.resolve(e.get("exec").asText()));
                    String key = e.get("key").asText();
                    long ms = e.get("durationMs").asLong();
                    tests.merge(key, new CoverageMap.TestCoverage(suite, files, ms), (a, b) -> {
                        SortedSet<String> union = new TreeSet<>(a.files());
                        union.addAll(b.files());
                        return new CoverageMap.TestCoverage(a.suite(), union, a.durationMs() + b.durationMs());
                    });
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return CoverageMap.of(Instant.now().toString(), commit, tests);
    }

    SortedSet<String> filesHit(Path execFile) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        try (InputStream in = Files.newInputStream(execFile)) {
            loader.load(in);
        }
        SortedSet<String> files = new TreeSet<>();
        for (ExecutionData data : loader.getExecutionDataStore().getContents()) {
            if (data.hasHits()) {
                sourceFor(data.getName()).ifPresent(files::add);
            }
        }
        return files;
    }

    /** {@code io/x/Foo$Bar$1} → repository-relative path of {@code io/x/Foo.java}, if it is ours. */
    Optional<String> sourceFor(String vmClassName) {
        int dollar = vmClassName.indexOf('$');
        String outer = dollar < 0 ? vmClassName : vmClassName.substring(0, dollar);
        return sourceCache.computeIfAbsent(outer, o -> {
            for (Path root : sourceRoots) {
                Path candidate = root.resolve(o + ".java");
                if (Files.exists(repoRoot.resolve(candidate))) {
                    return Optional.of(candidate.toString().replace('\\', '/'));
                }
            }
            return Optional.empty();
        });
    }
}
