package io.github.raktimchandra.testharness.core.logging;

import java.util.Map;
import org.slf4j.MDC;

/**
 * Structured-logging context. Values put here appear as JSON fields on every log line
 * written while they are set (see {@code harness-logback.xml}), so a run's logs can be
 * filtered per test, per browser or per run.
 */
public final class TestLog {

    public static final String RUN_ID = "runId";
    public static final String TEST_ID = "testId";
    public static final String TEST_NAME = "testName";

    private TestLog() {}

    public static void put(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static Map<String, String> snapshot() {
        Map<String, String> m = MDC.getCopyOfContextMap();
        return m == null ? Map.of() : m;
    }

    /** Runs {@code action} with extra context, restoring the previous context afterwards. */
    public static void with(Map<String, String> context, Runnable action) {
        Map<String, String> before = MDC.getCopyOfContextMap();
        try {
            context.forEach(TestLog::put);
            action.run();
        } finally {
            if (before == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(before);
            }
        }
    }
}
