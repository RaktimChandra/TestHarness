package io.github.raktimchandra.testharness.ui.heal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raktimchandra.testharness.reporting.Report;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The record of heals: per test (thread) for the report, and appended to
 * {@code target/healing/healing.jsonl} so CI can count and list every healed test. A heal is
 * never silent: it is logged as a warning, shown as a broken (yellow) step, tagged
 * {@code healed} in Allure and written to the file.
 */
public final class Healing {

    private static final Logger log = LoggerFactory.getLogger(Healing.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ThreadLocal<List<HealingEvent>> CURRENT = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<String> TEST = new ThreadLocal<>();
    static volatile Path file = Path.of("target", "healing", "healing.jsonl");

    private Healing() {}

    /** Called by the browser extension when a test starts. */
    public static void startTest(String testName) {
        TEST.set(testName);
        CURRENT.get().clear();
    }

    public static String currentTest() {
        String t = TEST.get();
        return t == null ? "(outside a test)" : t;
    }

    public static List<HealingEvent> events() {
        return List.copyOf(CURRENT.get());
    }

    static void record(HealingEvent e) {
        CURRENT.get().add(e);
        log.warn("HEALED locator in {}: '{}' not found by {}; found by {}. Suggested fix: {}", e.test(), e.target(),
                e.brokenLocator(), e.usedLocator(), e.suggestedFix());
        Report.label("tag", "healed");
        Report.warning("HEALED '" + e.target() + "': " + e.brokenLocator() + " failed, used " + e.usedLocator()
                + " — fix: " + e.suggestedFix());
        try {
            Report.json("healing " + e.target(), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(e));
            synchronized (Healing.class) {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, JSON.writeValueAsString(e) + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException ex) {
            log.warn("Could not write healing record: {}", ex.toString());
        }
    }
}
