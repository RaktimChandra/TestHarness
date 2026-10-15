package io.github.raktimchandra.testharness.insights.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** History as JSON Lines: one result per line, so files from many runs can be concatenated. */
public final class HistoryJson {

    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private HistoryJson() {}

    public static void append(Path file, List<TestResult> results) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                for (TestResult r : results) {
                    w.write(MAPPER.writeValueAsString(r));
                    w.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write history to " + file, e);
        }
    }

    /**
     * Reads one .jsonl file, or every history file under a directory. In a directory, a
     * {@code .jsonl} file counts as history when its first record has a {@code runId}; other
     * JSON Lines files (healing records, for example) are skipped.
     */
    public static List<TestResult> read(Path fileOrDir) {
        List<TestResult> out = new ArrayList<>();
        try (Stream<Path> files = Files.isDirectory(fileOrDir)
                ? Files.walk(fileOrDir).filter(HistoryJson::isHistoryFile).sorted()
                : Stream.of(fileOrDir)) {
            for (Path f : files.toList()) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        out.add(MAPPER.readValue(line, TestResult.class));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read history from " + fileOrDir + ": " + e.getMessage(), e);
        }
        return out;
    }

    static boolean isHistoryFile(Path p) {
        if (!p.getFileName().toString().endsWith(".jsonl") || !Files.isRegularFile(p)) {
            return false;
        }
        try (Stream<String> lines = Files.lines(p, StandardCharsets.UTF_8)) {
            return lines.filter(l -> !l.isBlank()).findFirst()
                    .map(l -> {
                        try {
                            return MAPPER.readTree(l).has("runId");
                        } catch (IOException e) {
                            return false;
                        }
                    }).orElse(false);
        } catch (IOException | UncheckedIOException e) {
            return false;
        }
    }
}
