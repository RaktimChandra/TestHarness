package io.github.raktimchandra.testharness.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathResource;

import io.github.raktimchandra.testharness.bdd.fixture.HookFixtureSteps;
import io.github.raktimchandra.testharness.core.logging.TestLog;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** Runs a real feature through the Cucumber engine with ScenarioHooks on the glue path. */
class ScenarioHooksTest {

    @Test
    void contextForEveryScenarioBrowserAndEvidenceForFailingUiScenarios() throws Exception {
        Path evidence = Path.of("target", "evidence");
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClasspathResource("hooks-features"))
                .filters(org.junit.platform.launcher.EngineFilter.includeEngines("cucumber"))
                .configurationParameter("cucumber.glue",
                        "io.github.raktimchandra.testharness.bdd.hooks,io.github.raktimchandra.testharness.bdd.fixture")
                .configurationParameter("cucumber.plugin", "summary")
                .configurationParameter("cucumber.execution.parallel.enabled", "false")
                .build(), listener);
        TestExecutionSummary summary = listener.getSummary();

        assertThat(summary.getTestsFoundCount()).isEqualTo(2);
        assertThat(summary.getTestsSucceededCount()).isEqualTo(1);
        assertThat(summary.getTestsFailedCount()).isEqualTo(1);
        // plain scenario: log context set, no browser
        assertThat(HookFixtureSteps.CONTEXTS).singleElement().satisfies(ctx -> {
            assertThat(ctx).containsKeys(TestLog.RUN_ID, TestLog.TEST_ID);
            assertThat(ctx).containsEntry(TestLog.TEST_NAME, "a plain scenario gets log context");
        });
        assertThat(HookFixtureSteps.BROWSER_OPEN).containsExactly(false, true);
        // ui scenario: evidence captured before the browser was quit, and nothing left open
        try (Stream<Path> files = Files.walk(evidence)) {
            assertThat(files.map(p -> p.toString().replace('\\', '/')))
                    .anyMatch(p -> p.contains("a_ui_scenario_that_fails-chrome") && p.endsWith("screenshot.png"))
                    .anyMatch(p -> p.contains("a_ui_scenario_that_fails-chrome") && p.endsWith("page.html"));
        }
        assertThat(Drivers.session()).isEmpty();
        // context is removed after each scenario
        assertThat(TestLog.snapshot()).doesNotContainKeys(TestLog.TEST_ID, TestLog.TEST_NAME);
    }
}
