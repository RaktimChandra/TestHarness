package io.github.raktimchandra.testharness.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

/** Runs a child process in the foreground, sharing this terminal. */
final class Processes {

    private Processes() {}

    static int run(List<String> command, Path dir, PrintWriter out) {
        out.println("> " + String.join(" ", command));
        out.flush();
        try {
            return new ProcessBuilder(command).directory(dir.toFile()).inheritIO().start().waitFor();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start " + command.get(0) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }
}
