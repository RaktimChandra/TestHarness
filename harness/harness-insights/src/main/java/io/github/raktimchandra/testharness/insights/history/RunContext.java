package io.github.raktimchandra.testharness.insights.history;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.core.logging.RunId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Where and on what a run happened: commit, branch, suite and environment labels.
 *
 * <p>A run with seeded bugs switched on ({@code harness.sut.seeded-bugs}) is not the code at its
 * commit, so its commit identity becomes {@code <sha>+seeded:<bugs>}. Otherwise every
 * fault-seeding or TIA experiment run would look like a same-commit flip and poison the
 * flakiness scores with deliberate failures.
 */
public record RunContext(String runId, String commitSha, String branch, String suite, String environment) {

    public static RunContext detect(HarnessConfig config) {
        String commit = config.find("history.commit")
                .or(() -> env("GITHUB_SHA"))
                .or(() -> git("rev-parse", "HEAD"))
                .orElse("unknown");
        String seeded = config.string("sut.seeded-bugs", "").strip();
        if (!seeded.isEmpty()) {
            commit = commit + "+seeded:" + seeded;
        }
        String branch = config.find("history.branch")
                .or(() -> env("GITHUB_HEAD_REF"))
                .or(() -> env("GITHUB_REF_NAME"))
                .or(() -> git("rev-parse", "--abbrev-ref", "HEAD"))
                .orElse("unknown");
        String suite = config.find("history.suite")
                .orElse(Path.of(System.getProperty("user.dir")).getFileName().toString());
        String environment = config.environment().name().toLowerCase() + "/" + config.string("ui.browser", "chrome");
        return new RunContext(config.find("history.run-id").orElse(RunId.value()), commit, branch, suite, environment);
    }

    private static Optional<String> env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    private static Optional<String> git(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0) {
                p.destroyForcibly();
                return Optional.empty();
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
