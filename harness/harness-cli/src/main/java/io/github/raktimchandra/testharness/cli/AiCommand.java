package io.github.raktimchandra.testharness.cli;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.ai.FailureExplainer;
import io.github.raktimchandra.testharness.insights.ai.GherkinDrafter;
import io.github.raktimchandra.testharness.insights.ai.LlmClient;
import io.github.raktimchandra.testharness.insights.ai.LlmClients;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "ai", mixinStandardHelpOptions = true, description = {"Optional AI assistance. Everything it writes is a draft under target/ai-drafts.",
        "Configure with -Dharness.ai.provider=openai|anthropic -Dharness.ai.model=<model> and HARNESS_AI_API_KEY."},
        subcommands = {AiCommand.Gherkin.class, AiCommand.Explain.class})
class AiCommand {

    @Command(name = "gherkin", mixinStandardHelpOptions = true, description = "Draft a feature file from a user story (needs a configured model).")
    static class Gherkin implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @ArgGroup(multiplicity = "1")
        StorySource story;

        @Option(names = "--no-step-definitions",
                description = "offer the model only the phrases of existing features, not the suite's step catalogue"
                        + " (suites/bdd-suite/src/test/resources/step-definitions.txt)")
        boolean noStepDefinitions;

        static class StorySource {
            @Option(names = "--story", description = "the user story text")
            String text;

            @Option(names = "--story-file", description = "file containing the user story")
            Path file;
        }

        @Override
        public Integer call() throws Exception {
            Repo repo = Repo.find();
            LlmClient llm = LlmClients.fromConfig(HarnessConfig.get()).orElseThrow(() -> new IllegalStateException(
                    "No AI provider configured (ai.provider=none). The framework does not need one; to draft,"
                            + " set -Dharness.ai.provider, -Dharness.ai.model and HARNESS_AI_API_KEY."));
            String text = story.text != null ? story.text : Files.readString(story.file);
            List<Path> features;
            try (Stream<Path> s = Files.walk(repo.root().resolve("suites/bdd-suite/src/test/resources/features"))) {
                features = s.filter(p -> p.toString().endsWith(".feature")).toList();
            }
            Path catalogue = repo.root().resolve("suites/bdd-suite/src/test/resources/step-definitions.txt");
            List<String> definitions = !noStepDefinitions && Files.exists(catalogue)
                    ? Files.readAllLines(catalogue).stream().filter(l -> !l.isBlank()).toList() : List.of();
            GherkinDrafter.Draft d = new GherkinDrafter(llm, repo.root().resolve("target/ai-drafts"))
                    .draft(text, features, definitions);
            PrintWriter out = spec.commandLine().getOut();
            out.println(definitions.isEmpty() ? "Offered the model the phrases of existing features."
                    : "Offered the model " + definitions.stream().filter(l -> !l.startsWith("#")).count()
                            + " step definitions and the phrases of existing features.");
            out.println("DRAFT written to " + d.file() + (d.parses() ? " (" + d.scenarios() + " scenarios, valid Gherkin)"
                    : " (NOT valid Gherkin: " + d.parseError().orElse("") + ")"));
            out.println("Review it before moving it into src/test/resources/features.");
            return 0;
        }
    }

    @Command(name = "explain", mixinStandardHelpOptions = true, description = "Summarise the last run's failures (works without AI; adds a draft with it).")
    static class Explain implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Option(names = "--send-screenshots", description = "attach failure screenshots to the model (off by default)")
        boolean sendScreenshots;

        @Override
        public Integer call() {
            Repo repo = Repo.find();
            Optional<LlmClient> llm = LlmClients.fromConfig(HarnessConfig.get());
            FailureExplainer.Result r = new FailureExplainer(llm, sendScreenshots)
                    .explain(repo.root(), repo.root().resolve("target/ai-drafts"));
            spec.commandLine().getOut().printf("%d failure(s) %s; summary: %s%s%n", r.failures(), r.byCause(), r.file(),
                    r.usedModel() ? " (includes an AI draft)" : "");
            return 0;
        }
    }
}
