package io.github.raktimchandra.testharness.cli;

import java.nio.file.Files;
import java.nio.file.Path;

/** The repository the CLI works in: the nearest ancestor holding the Maven Wrapper. */
record Repo(Path root) {

    static Repo find() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("mvnw")) && Files.exists(dir.resolve("pom.xml"))) {
                return new Repo(dir);
            }
        }
        throw new IllegalStateException("Not inside the TestHarness repository (no mvnw found above "
                + Path.of("").toAbsolutePath() + ")");
    }

    String mavenWrapper() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                ? root.resolve("mvnw.cmd").toString() : root.resolve("mvnw").toString();
    }
}
