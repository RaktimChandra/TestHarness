package io.github.raktimchandra.testharness.insights.select;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Finds files relative to the repository root, whichever module directory a test runs in. */
public final class RepoFiles {

    private RepoFiles() {}

    /** Resolves {@code configured} as given if absolute or present; otherwise looks for it in ancestors. */
    public static Optional<Path> locate(String configured) {
        Path p = Path.of(configured);
        if (p.isAbsolute() || Files.exists(p)) {
            return Files.exists(p) ? Optional.of(p) : Optional.empty();
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(configured);
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
            if (Files.exists(dir.resolve(".git"))) {
                break;
            }
        }
        return Optional.empty();
    }
}
