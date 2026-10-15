package io.github.raktimchandra.testharness.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "run", mixinStandardHelpOptions = true, description = {"Run suites with the usual options, e.g.",
        "  harness run --suite ui --browser firefox --tags smoke",
        "  harness run --suite api,bdd --env docker",
        "Extra Maven arguments go after --, e.g. harness run --suite api -- -o"})
class RunCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = {"-s", "--suite"}, split = ",", description = "api, ui, bdd, perf or all (default all)")
    List<String> suites = new ArrayList<>();

    @Option(names = {"-t", "--tags"}, description = "JUnit tag expression, e.g. 'smoke & !visual'")
    String tags;

    @Option(names = "--bdd-tags", description = "Cucumber tag expression, e.g. '@smoke and not @ui'")
    String bddTags;

    @Option(names = {"-b", "--browser"}, description = "chrome, firefox or edge")
    String browser;

    @Option(names = {"-e", "--env"}, description = "local, docker or ci")
    String env;

    @Option(names = "--grid", description = "Selenium Grid URL, e.g. http://localhost:4444")
    String grid;

    @Option(names = "--headed", description = "show the browser")
    boolean headed;

    @Option(names = "--shard", description = "run only shard k of n, e.g. 2/4")
    String shard;

    @Option(names = "--select", description = "file of test keys to run")
    String select;

    @Option(names = "--quarantine", description = "off (default), exclude or only")
    String quarantine;

    @Option(names = "--seeded-bugs", description = "comma-separated seeded bugs to switch on")
    String seededBugs;

    @Option(names = {"-w", "--workers"}, description = "parallel test workers")
    Integer workers;

    @Option(names = "--also-make", description = "also build the modules the suites depend on (-am)")
    boolean alsoMake;

    @Option(names = "--dry-run", description = "print the Maven command without running it")
    boolean dryRun;

    @Parameters(arity = "0..*", description = "extra Maven arguments (after --)")
    List<String> extra = new ArrayList<>();

    @Override
    public Integer call() {
        Repo repo = Repo.find();
        List<String> cmd = MavenCommand.build(repo.mavenWrapper(), new MavenCommand.Options(suites, tags, bddTags,
                browser, env, grid, headed, shard, select, quarantine, seededBugs, workers, alsoMake, extra));
        if (dryRun) {
            spec.commandLine().getOut().println(String.join(" ", cmd));
            return 0;
        }
        return Processes.run(cmd, repo.root(), spec.commandLine().getOut());
    }
}
