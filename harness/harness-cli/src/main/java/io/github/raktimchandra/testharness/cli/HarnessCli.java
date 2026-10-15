package io.github.raktimchandra.testharness.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * {@code harness} — one entry point for running and analysing the suites.
 * Run {@code ./harness --help} (or {@code harness.cmd --help} on Windows).
 */
@Command(name = "harness", mixinStandardHelpOptions = true, version = "TestHarness 1.0.0",
        description = "Run the TestHarness suites and analyse their results.",
        subcommands = {RunCommand.class, RerunFailedCommand.class, ReportCommand.class, HistoryCommand.class,
                TiaCommand.class, AiCommand.class, CommandLine.HelpCommand.class})
public final class HarnessCli {

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    static CommandLine commandLine() {
        CommandLine cl = new CommandLine(new HarnessCli());
        cl.setExecutionExceptionHandler((ex, cmd, parse) -> {
            cmd.getErr().println(cmd.getColorScheme().errorText("error: " + ex.getMessage()));
            return 2;
        });
        return cl;
    }
}
