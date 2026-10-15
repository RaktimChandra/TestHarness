package io.github.raktimchandra.testharness.ui.junit;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import io.github.raktimchandra.testharness.ui.driver.UiSettings;
import io.github.raktimchandra.testharness.ui.evidence.UiEvidence;
import io.github.raktimchandra.testharness.ui.heal.Healing;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.openqa.selenium.WebDriver;

/**
 * Gives each test a fresh browser on its own thread, captures evidence when the test fails
 * (before the browser is closed) and always quits the browser afterwards. Tests can declare
 * {@link WebDriver} or {@link Waits} parameters.
 */
public class BrowserExtension implements BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback,
        ParameterResolver {

    @Override
    public void beforeEach(ExtensionContext context) {
        UiSettings settings = UiSettings.from(HarnessConfig.get());
        Drivers.start(settings);
        Healing.startTest(context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName());
        Report.parameter("browser", settings.browser().id());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isPresent()) {
            Drivers.session().ifPresent(s -> UiEvidence.capture(s.driver(), s.console(),
                    context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName()
                            + "-" + s.settings().browser().id(),
                    Path.of("target", "evidence")));
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Drivers.quit();
    }

    @Override
    public boolean supportsParameter(ParameterContext p, ExtensionContext e) {
        Class<?> type = p.getParameter().getType();
        return type == WebDriver.class || type == Waits.class;
    }

    @Override
    public Object resolveParameter(ParameterContext p, ExtensionContext e) {
        if (p.getParameter().getType() == WebDriver.class) {
            return Drivers.current();
        }
        UiSettings s = Drivers.session().orElseThrow().settings();
        return new Waits(Drivers.current(), s.explicitWait(), s.pollInterval());
    }
}
