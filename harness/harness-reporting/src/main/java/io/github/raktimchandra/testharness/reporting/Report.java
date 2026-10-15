package io.github.raktimchandra.testharness.reporting;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one reporting API the framework and suites use. It writes to Allure and to the log,
 * so evidence exists even when a run is looked at without an Allure report.
 */
public final class Report {

    private static final Logger log = LoggerFactory.getLogger(Report.class);

    private Report() {}

    /** Runs {@code action} as a named report step; a thrown exception marks the step failed. */
    public static <T> T step(String name, Callable<T> action) {
        log.info("STEP {}", name);
        return Allure.step(name, () -> action.call());
    }

    public static void step(String name, Runnable action) {
        step(name, () -> {
            action.run();
            return null;
        });
    }

    /** A step with no body (a note in the timeline), e.g. "Seeded bugs: none". */
    public static void note(String message) {
        log.info("NOTE {}", message);
        Allure.step(message, Status.PASSED);
    }

    /** A label on the current test result, e.g. {@code label("tag", "healed")}; filterable in Allure. */
    public static void label(String name, String value) {
        Allure.label(name, value);
    }

    /** A step marked as broken (yellow in Allure): something the reader must look at, though the test went on. */
    public static void warning(String message) {
        log.warn("WARNING {}", message);
        Allure.step(message, Status.BROKEN);
    }

    public static void parameter(String name, Object value) {
        Allure.parameter(name, value);
    }

    public static void text(String name, String content) {
        attach(name, "text/plain", ".txt", content == null ? "" : content);
    }

    public static void json(String name, String content) {
        attach(name, "application/json", ".json", content == null ? "" : content);
    }

    public static void html(String name, String content) {
        attach(name, "text/html", ".html", content == null ? "" : content);
    }

    public static void png(String name, byte[] bytes) {
        Allure.getLifecycle().addAttachment(name, "image/png", ".png", bytes);
    }

    private static void attach(String name, String type, String ext, String content) {
        Allure.getLifecycle().addAttachment(name, type, ext, content.getBytes(StandardCharsets.UTF_8));
    }
}
