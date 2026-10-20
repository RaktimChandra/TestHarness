package io.github.raktimchandra.testharness.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * {@code harness} — one entry point for running and analysing the suites.
 * Run {@code ./harness --help} (or {@code harness.cmd --help} on Windows).
 */
@Command(name = "harness", mixinStandardHelpOptions = true, versionProvider = HarnessCli.Version.class,
        description = "Run the TestHarness suites and analyse their results.",
        subcommands = {RunCommand.class, RerunFailedCommand.class, ReportCommand.class, HistoryCommand.class,
                TiaCommand.class, AiCommand.class, DashboardCommand.class, CommandLine.HelpCommand.class})
public final class HarnessCli {

    /**
     * The version from the jar's manifest, written by the build from the POM, so it cannot
     * drift from the release (it said 1.0.0 through 1.1.0 while it was a constant here).
     */
    static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"TestHarness " + of(HarnessCli.class.getPackage().getImplementationVersion())};
        }

        static String of(String implementationVersion) {
            return implementationVersion == null || implementationVersion.isBlank()
                    ? "(development build, not from a jar)" : implementationVersion;
        }
    }

    public static void main(String[] args) {
        System.exit(commandLine().execute(applySystemProperties(args)));
    }

    /**
     * {@code -Dname=value} anywhere on the command line becomes a system property (as with
     * {@code java -D}), so {@code ./th ai gherkin -Dharness.ai.provider=openai ...} works
     * through the launcher. Run before any configuration is read. Returns the other arguments.
     */
    static String[] applySystemProperties(String[] args) {
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (String a : args) {
            if (a.startsWith("-D") && a.indexOf('=') > 2) {
                System.setProperty(a.substring(2, a.indexOf('=')), a.substring(a.indexOf('=') + 1));
            } else {
                rest.add(a);
            }
        }
        if (rest.size() != args.length) {
            io.github.raktimchandra.testharness.core.config.HarnessConfig.reset();
        }
        return rest.toArray(String[]::new);
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
