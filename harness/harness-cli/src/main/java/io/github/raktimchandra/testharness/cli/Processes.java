package io.github.raktimchandra.testharness.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

/** Runs a child process in the foreground, sharing this terminal. */
final class Processes {

    private Processes() {}

    /**
     * cmd.exe splits arguments at '=', ',' and ';' unless they are quoted, so on Windows a
     * batch file (mvnw.cmd) would receive "-Dname=value" as "-Dname" and "value" and Maven would
     * see "-Dname=" (found by the Windows CI job). Such arguments are quoted for batch files.
     */
    static List<String> forPlatform(List<String> command, String osName) {
        boolean batch = osName.toLowerCase(java.util.Locale.ROOT).contains("win") && !command.isEmpty()
                && command.get(0).toLowerCase(java.util.Locale.ROOT).matches(".*\\.(cmd|bat)");
        if (!batch) {
            return command;
        }
        List<String> out = new java.util.ArrayList<>(command.size());
        out.add(command.get(0));
        for (String a : command.subList(1, command.size())) {
            boolean special = a.chars().anyMatch(c -> c == '=' || c == ',' || c == ';' || c == ' ');
            out.add(special && !a.startsWith("\"") ? "\"" + a + "\"" : a);
        }
        return out;
    }

    static int run(List<String> command, Path dir, PrintWriter out) {
        out.println("> " + String.join(" ", command));
        out.flush();
        try {
            return new ProcessBuilder(forPlatform(command, System.getProperty("os.name", "")))
                    .directory(dir.toFile()).inheritIO().start().waitFor();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start " + command.get(0) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }
}
