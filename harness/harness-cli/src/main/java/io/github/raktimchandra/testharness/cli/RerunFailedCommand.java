package io.github.raktimchandra.testharness.cli;

import io.github.raktimchandra.testharness.insights.report.SurefireReports;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "rerun-failed", mixinStandardHelpOptions = true, description = {"Re-run only the tests that failed in the last run.",
        "Reads every target/surefire-reports below the repository, writes the failed test keys to",
        "target/harness/rerun-failed.txt and runs just those (needs the modules installed: run once with",
        "'mvnw install -DskipTests' if Maven reports missing artifacts)."})
class RerunFailedCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--dry-run", description = "list the tests and the command without running")
    boolean dryRun;

    @Parameters(arity = "0..*", description = "extra Maven arguments (after --)")
    List<String> extra = new ArrayList<>();

    @Override
    public Integer call() {
        Repo repo = Repo.find();
        PrintWriter out = spec.commandLine().getOut();
        Map<String, TreeSet<String>> byModule = failedByModule(repo.root());
        if (byModule.isEmpty()) {
            out.println("No failed tests in the last run's reports. Nothing to re-run.");
            return 0;
        }
        Path selectFile = repo.root().resolve("target/harness/rerun-failed.txt");
        List<String> lines = new ArrayList<>(List.of("# failed tests from the last run")); 
        byModule.values().forEach(lines::addAll);
        try {
            Files.createDirectories(selectFile.getParent());
            Files.write(selectFile, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byModule.forEach((m, keys) -> {
            out.println(m + ":");
            keys.forEach(k -> out.println("  " + k));
        });
        List<String> cmd = new ArrayList<>(List.of(repo.mavenWrapper(), "-B", "verify", "-pl",
                String.join(",", byModule.keySet()), "-Dharness.select=" + selectFile,
                "-Dsurefire.failIfNoSpecifiedTests=false"));
        cmd.addAll(extra);
        if (dryRun) {
            out.println(String.join(" ", cmd));
            return 0;
        }
        return Processes.run(cmd, repo.root(), out);
    }

    static Map<String, TreeSet<String>> failedByModule(Path root) {
        Map<String, TreeSet<String>> out = new TreeMap<>();
        for (SurefireReports.Case c : SurefireReports.read(root)) {
            if (c.failed() && !c.module().isBlank()) {
                out.computeIfAbsent(c.module(), m -> new TreeSet<>()).add(c.key());
            }
        }
        return out;
    }
}
