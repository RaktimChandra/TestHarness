package io.github.raktimchandra.testharness.reporting;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.nio.file.Path;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs once per test JVM (registered through {@code META-INF/services}): writes Allure run
 * metadata and restores trend history before any test starts.
 */
public class ReportingSessionListener implements LauncherSessionListener {

    private static final Logger log = LoggerFactory.getLogger(ReportingSessionListener.class);

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        String results = System.getProperty("allure.results.directory");
        if (results == null) {
            return; // Allure not configured for this JVM (e.g. plain IDE run)
        }
        try {
            Path resultsDir = Path.of(results);
            AllureRunMetadata.write(resultsDir, HarnessConfig.get(), System::getenv);
            String history = System.getProperty("harness.report.history-dir");
            if (history != null) {
                int n = AllureHistory.restore(Path.of(history), resultsDir);
                log.info("Restored {} Allure history files from {}", n, history);
            }
        } catch (RuntimeException e) {
            // Reporting must never stop tests from running.
            log.warn("Could not prepare Allure metadata: {}", e.toString());
        }
    }
}
