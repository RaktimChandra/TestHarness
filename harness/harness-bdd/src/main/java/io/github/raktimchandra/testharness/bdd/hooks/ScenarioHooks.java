package io.github.raktimchandra.testharness.bdd.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.core.logging.RunId;
import io.github.raktimchandra.testharness.core.logging.TestLog;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.driver.UiSettings;
import io.github.raktimchandra.testharness.ui.evidence.UiEvidence;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-cutting scenario behaviour. Add {@code io.github.raktimchandra.testharness.bdd.hooks}
 * to the glue path to enable it.
 *
 * <ul>
 *   <li>every scenario: structured-log context (run, scenario, tags)</li>
 *   <li>{@code @ui} scenarios: a browser is started before and always quit after; on failure
 *       a screenshot, page source and console log are attached first</li>
 * </ul>
 */
public class ScenarioHooks {

    private static final Logger log = LoggerFactory.getLogger(ScenarioHooks.class);

    @Before(order = 0)
    public void context(Scenario scenario) {
        TestLog.put(TestLog.RUN_ID, RunId.value());
        TestLog.put(TestLog.TEST_ID, scenario.getId());
        TestLog.put(TestLog.TEST_NAME, scenario.getName());
        log.info("SCENARIO {} {}", scenario.getName(), scenario.getSourceTagNames());
    }

    @Before(value = "@ui", order = 10)
    public void startBrowser() {
        Drivers.start(UiSettings.from(HarnessConfig.get()));
    }

    @After(value = "@ui", order = 10)
    public void stopBrowser(Scenario scenario) {
        try {
            if (scenario.isFailed()) {
                Drivers.session().ifPresent(s -> UiEvidence.capture(s.driver(), s.console(),
                        scenario.getName() + "-" + s.settings().browser().id(), Path.of("target", "evidence")));
            }
        } finally {
            Drivers.quit();
        }
    }

    @After(order = 0)
    public void clear(Scenario scenario) {
        log.info("SCENARIO {} -> {}", scenario.getName(), scenario.getStatus());
        TestLog.remove(TestLog.TEST_ID);
        TestLog.remove(TestLog.TEST_NAME);
    }
}
