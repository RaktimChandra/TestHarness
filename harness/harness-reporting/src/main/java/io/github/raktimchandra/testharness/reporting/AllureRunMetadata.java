package io.github.raktimchandra.testharness.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.core.logging.RunId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Writes the Allure side files: {@code environment.properties} (what ran where),
 * {@code executor.json} (which CI build) and {@code categories.json} (failure buckets that
 * mirror the framework's failure classifier: locator, timeout, assertion, environment, data).
 */
public final class AllureRunMetadata {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private AllureRunMetadata() {}

    public static void write(Path resultsDir, HarnessConfig config, Function<String, String> env) {
        try {
            Files.createDirectories(resultsDir);
            writeEnvironment(resultsDir.resolve("environment.properties"), environment(config, env));
            JSON.writeValue(resultsDir.resolve("categories.json").toFile(), categories());
            Map<String, Object> executor = executor(env);
            if (!executor.isEmpty()) {
                JSON.writeValue(resultsDir.resolve("executor.json").toFile(), executor);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write Allure run metadata to " + resultsDir, e);
        }
    }

    static Map<String, String> environment(HarnessConfig config, Function<String, String> env) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Run.Id", RunId.value());
        m.put("Harness.Env", config.environment().name().toLowerCase());
        config.find("sut.base-url").ifPresent(v -> m.put("SUT.BaseUrl", v));
        config.find("sut.seeded-bugs").ifPresent(v -> m.put("SUT.SeededBugs", v));
        m.put("UI.Browser", config.string("ui.browser", "chrome"));
        m.put("UI.Headless", config.string("ui.headless", "true"));
        config.find("ui.grid-url").ifPresent(v -> m.put("UI.Grid", v));
        m.put("Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        m.put("OS", System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch"));
        m.put("CPUs", String.valueOf(Runtime.getRuntime().availableProcessors()));
        String sha = env.apply("GITHUB_SHA");
        if (sha != null) {
            m.put("Git.Commit", sha);
        }
        return m;
    }

    static Map<String, Object> executor(Function<String, String> env) {
        String server = env.apply("GITHUB_SERVER_URL");
        String repo = env.apply("GITHUB_REPOSITORY");
        String runId = env.apply("GITHUB_RUN_ID");
        if (server == null || repo == null || runId == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "GitHub Actions");
        m.put("type", "github");
        m.put("url", server + "/" + repo + "/actions");
        m.put("buildOrder", runId);
        m.put("buildName", env.apply("GITHUB_WORKFLOW") + " #" + env.apply("GITHUB_RUN_NUMBER"));
        m.put("buildUrl", server + "/" + repo + "/actions/runs/" + runId);
        return m;
    }

    static List<Map<String, Object>> categories() {
        return List.of(
                category("Locator broken", List.of("broken", "failed"),
                        ".*(NoSuchElementException|StaleElementReferenceException|InvalidSelectorException).*"),
                category("Timeout", List.of("broken", "failed"), ".*(TimeoutException|AwaitTimeoutException).*"),
                category("Environment / infrastructure", List.of("broken"),
                        ".*(ConnectException|Connection refused|ContainerLaunchException|SessionNotCreated"
                                + "|UnreachableBrowserException|UnknownHostException).*"),
                category("Contract / schema violation", List.of("failed"), ".*(Schema validation failed).*"),
                category("Product defect (assertion)", List.of("failed"), ".*"),
                category("Test defect (unexpected exception)", List.of("broken"), ".*"));
    }

    private static Map<String, Object> category(String name, List<String> statuses, String messageRegex) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("matchedStatuses", statuses);
        m.put("messageRegex", messageRegex);
        return m;
    }

    private static void writeEnvironment(Path file, Map<String, String> values) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue().replace("\\", "\\\\").replace("\n", " ") + "\n");
            }
        }
    }
}
