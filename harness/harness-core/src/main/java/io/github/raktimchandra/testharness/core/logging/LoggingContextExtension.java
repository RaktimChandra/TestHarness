package io.github.raktimchandra.testharness.core.logging;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Puts the run id, test id and display name into the logging context for the duration of
 * each test. Registered automatically via {@code junit-platform.properties}.
 */
public class LoggingContextExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        TestLog.put(TestLog.RUN_ID, RunId.value());
        TestLog.put(TestLog.TEST_ID, context.getUniqueId());
        TestLog.put(TestLog.TEST_NAME, context.getRequiredTestClass().getSimpleName() + "." + context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestLog.remove(TestLog.TEST_ID);
        TestLog.remove(TestLog.TEST_NAME);
    }
}
