package io.github.raktimchandra.testharness.insights.ai;

import io.github.raktimchandra.testharness.insights.classify.FailureCause;
import io.github.raktimchandra.testharness.insights.classify.FailureClassifier;
import io.github.raktimchandra.testharness.insights.report.SurefireReports;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Summarises a failed run. The deterministic part (failures grouped by cause and message, with
 * evidence paths) is always produced, so the feature is fully useful without any AI. With a
 * configured model, a "likely cause" section is added — clearly marked as an unverified draft.
 */
public final class FailureExplainer {

    public record Result(Path file, int failures, Map<FailureCause, Integer> byCause, boolean usedModel) {}

    static final String SYSTEM = """
            You help a QA engineer triage a failed automated test run. Given the failures, their
            classification, stack trace excerpts and browser evidence, say which failures most
            likely share a root cause, what that cause probably is, whether it looks like a product
            bug, a test bug or an environment problem, and the first thing to check. Be specific and
            brief. Say when the evidence is insufficient. Never invent file names or log lines.
            """;

    private final Optional<LlmClient> llm;
    private final boolean sendScreenshots;

    public FailureExplainer(Optional<LlmClient> llm, boolean sendScreenshots) {
        this.llm = llm;
        this.sendScreenshots = sendScreenshots;
    }

    /**
     * @param repoRoot  where to look for surefire reports and {@code target/evidence}
     * @param outputDir where the summary markdown is written
     */
    public Result explain(Path repoRoot, Path outputDir) {
        List<SurefireReports.Case> failed = SurefireReports.read(repoRoot).stream()
                .filter(SurefireReports.Case::failed).toList();
        Map<FailureCause, Integer> byCause = new EnumMap<>(FailureCause.class);
        StringBuilder digest = new StringBuilder();
        List<LlmClient.Image> images = new ArrayList<>();
        for (SurefireReports.Case c : failed) {
            FailureCause cause = FailureClassifier.classify(c.failureType(), c.message() + " " + c.stackTrace());
            byCause.merge(cause, 1, Integer::sum);
            digest.append("### ").append(c.className()).append(" › ").append(c.name()).append('\n')
                    .append("- module: `").append(c.module()).append("`\n")
                    .append("- classified as: **").append(cause).append("**\n")
                    .append("- exception: `").append(c.failureType()).append("`\n")
                    .append("- message: ").append(oneLine(c.message())).append('\n');
            String stack = topOfStack(c.stackTrace());
            if (!stack.isBlank()) {
                digest.append("\n```\n").append(stack).append("\n```\n");
            }
            evidenceDir(repoRoot, c).ifPresent(dir -> {
                digest.append("- evidence: `").append(repoRoot.relativize(dir)).append("`\n");
                tail(dir.resolve("console.log"), 15).ifPresent(t -> digest.append("\nBrowser console (last lines):\n```\n")
                        .append(t).append("\n```\n"));
                Path shot = dir.resolve("screenshot.png");
                if (sendScreenshots && Files.exists(shot) && images.size() < 3) {
                    try {
                        images.add(new LlmClient.Image("image/png", Files.readAllBytes(shot)));
                    } catch (IOException ignored) {
                        // a missing screenshot only weakens the analysis
                    }
                }
            });
            digest.append('\n');
        }
        StringBuilder md = new StringBuilder("# Failed run summary\n\n")
                .append("_Generated ").append(Instant.now()).append(" from surefire reports under `")
                .append(repoRoot.toAbsolutePath().normalize()).append("`._\n\n");
        if (failed.isEmpty()) {
            md.append("No failed tests found.\n");
        } else {
            md.append("**").append(failed.size()).append(" failed test(s)** by cause: ").append(byCause).append("\n\n");
            boolean used = false;
            if (llm.isPresent()) {
                try {
                    String answer = llm.get().complete(SYSTEM, digest.toString(), images);
                    md.append("## Likely cause — DRAFT by ").append(llm.get().describe())
                            .append(" (unverified; check before acting)\n\n").append(answer.strip()).append("\n\n");
                    used = true;
                } catch (LlmException e) {
                    md.append("> AI summary unavailable: ").append(e.getMessage()).append("\n\n");
                }
            } else {
                md.append("> AI summary off (set `ai.provider`, `ai.model` and `HARNESS_AI_API_KEY` to add one).\n\n");
            }
            md.append("## Failures\n\n").append(digest);
            write(outputDir, md);
            return new Result(outputDir.resolve("failure-summary.md"), failed.size(), byCause, used);
        }
        write(outputDir, md);
        return new Result(outputDir.resolve("failure-summary.md"), 0, byCause, false);
    }

    private static void write(Path dir, CharSequence md) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("failure-summary.md"), md, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code target/evidence/<Class>_<method...>} as written by the UI evidence collector. */
    static Optional<Path> evidenceDir(Path root, SurefireReports.Case c) {
        Path evidence = root.resolve(c.module()).resolve("target/evidence");
        if (!Files.isDirectory(evidence)) {
            return Optional.empty();
        }
        String simple = c.className().substring(c.className().lastIndexOf('.') + 1);
        String method = c.name().contains("(") ? c.name().substring(0, c.name().indexOf('(')) : c.name();
        try (Stream<Path> dirs = Files.list(evidence)) {
            return dirs.filter(Files::isDirectory).filter(d -> {
                String n = d.getFileName().toString();
                return n.contains(simple) && n.contains(method.replaceAll("[^A-Za-z0-9]+", "_"));
            }).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static Optional<String> tail(Path file, int lines) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            return Optional.of(String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size())));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static String topOfStack(String stack) {
        if (stack == null) {
            return "";
        }
        return String.join("\n", stack.strip().lines().limit(12).toList());
    }

    private static String oneLine(String s) {
        if (s == null || s.isBlank()) {
            return "(none)";
        }
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() > 400 ? one.substring(0, 400) + "…" : one;
    }
}
