package io.github.raktimchandra.testharness.cli;

import io.github.raktimchandra.testharness.insights.tia.CoverageMap;
import io.github.raktimchandra.testharness.insights.tia.TiaMapper;
import io.github.raktimchandra.testharness.insights.tia.TiaSelector;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "tia", mixinStandardHelpOptions = true, description = "Test impact analysis.", subcommands = {TiaCommand.Map.class, TiaCommand.Select.class})
class TiaCommand {

    static final List<String> SUT_SOURCES = List.of("sut/gate-api/src/main/java", "sut/gate-domain/src/main/java");

    @Command(name = "map", mixinStandardHelpOptions = true, description = {"Build test-impact-map.json from per-test coverage recordings.",
            "Record first:  mvnw verify -pl suites/api-suite,suites/ui-suite,suites/bdd-suite -Dharness.tia.record=true",
            "               -Djunit.jupiter.execution.parallel.enabled=false"})
    static class Map implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Option(names = "--out", defaultValue = "target/tia/test-impact-map.json")
        Path out;

        @Option(names = "--commit", defaultValue = "unknown")
        String commit;

        @Override
        public Integer call() throws IOException {
            Repo repo = Repo.find();
            List<Path> dirs = new ArrayList<>();
            for (String s : List.of("suites/api-suite", "suites/ui-suite", "suites/bdd-suite")) {
                Path tia = repo.root().resolve(s).resolve("target/tia");
                if (Files.isDirectory(tia)) {
                    try (var list = Files.list(tia)) {
                        list.filter(Files::isDirectory).forEach(dirs::add);
                    }
                }
            }
            CoverageMap map = new TiaMapper(repo.root(), SUT_SOURCES).map(dirs, commit);
            Path target = repo.root().resolve(out);
            map.write(target);
            spec.commandLine().getOut().printf("%d tests, %d SUT files covered, Σ test time %.1f s -> %s%n",
                    map.tests().size(), map.testsByFile().size(), map.totalDurationMs() / 1000.0, target);
            return 0;
        }
    }

    @Command(name = "select", mixinStandardHelpOptions = true, description = "Pick the tests affected by a change and write a selection file.")
    static class Select implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Option(names = "--map", defaultValue = "target/tia/test-impact-map.json")
        Path map;

        @Option(names = "--base", description = "git ref to diff against, e.g. origin/main")
        String base;

        @Option(names = "--files", split = ",", description = "changed files instead of a git diff")
        List<String> files;

        @Option(names = "--out", defaultValue = "target/tia/selection.txt")
        Path out;

        @Override
        public Integer call() throws Exception {
            Repo repo = Repo.find();
            List<String> changed = files != null ? files : gitDiff(repo.root(), base == null ? "origin/main" : base);
            CoverageMap m = CoverageMap.read(repo.root().resolve(map));
            TiaSelector.Selection sel = new TiaSelector(m, Set.copyOf(SUT_SOURCES.stream().map(s -> s + "/")
                    .collect(Collectors.toList()))).select(changed);
            PrintWriter o = spec.commandLine().getOut();
            sel.reasons().forEach((f, r) -> o.println("  " + f + ": " + r));
            o.printf("%d of %d tests selected%s; estimated %.1f s of %.1f s test time%n", sel.tests().size(),
                    m.tests().size(), sel.runAll() ? " (run all)" : "", sel.selectedDurationMs() / 1000.0,
                    sel.fullDurationMs() / 1000.0);
            if (!sel.untestedChanges().isEmpty()) {
                o.println("WARNING: changed SUT files that no test executes: " + sel.untestedChanges());
            }
            Path target = repo.root().resolve(out);
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.write(target, sel.tests(), StandardCharsets.UTF_8);
            o.println("Selection written to " + target + "  (run with: harness run --select " + target + ")");
            return 0;
        }

        static List<String> gitDiff(Path root, String base) {
            try {
                Process p = new ProcessBuilder("git", "diff", "--name-only", base + "...HEAD").directory(root.toFile())
                        .redirectErrorStream(true).start();
                String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
                    throw new IllegalStateException("git diff failed: " + text.strip());
                }
                return text.lines().filter(l -> !l.isBlank()).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
