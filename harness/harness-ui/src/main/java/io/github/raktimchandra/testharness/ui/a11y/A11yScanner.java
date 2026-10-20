package io.github.raktimchandra.testharness.ui.a11y;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.raktimchandra.testharness.reporting.Report;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs axe-core against the current page with the WCAG 2.0/2.1 level A and AA rule sets,
 * records the result as JSON under {@code target/a11y/} (for CI summaries and the
 * dashboard), attaches it to the report, and checks it against an {@link A11yGate}.
 */
public final class A11yScanner {

    private static final Logger log = LoggerFactory.getLogger(A11yScanner.class);

    public static final List<String> WCAG_21_AA = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final WebDriver driver;
    private final String browser;
    private final Path outputDir;

    public A11yScanner(WebDriver driver, String browser, Path outputDir) {
        this.driver = driver;
        this.browser = browser;
        this.outputDir = outputDir;
    }

    public A11yReport scan(String page) {
        Results results = new AxeBuilder().withTags(WCAG_21_AA).analyze(driver);
        if (results.isErrored()) {
            throw new IllegalStateException("axe-core failed on " + page + ": " + results.getErrorMessage());
        }
        List<A11yReport.Violation> violations = new ArrayList<>();
        for (Rule rule : results.getViolations()) {
            List<String> targets = rule.getNodes().stream().map(A11yScanner::target).toList();
            violations.add(new A11yReport.Violation(rule.getId(), rule.getImpact(), rule.getHelp(), rule.getHelpUrl(),
                    rule.getNodes().size(), targets));
        }
        A11yReport report = new A11yReport(page, browser, driver.getCurrentUrl(), violations);
        write(report);
        return report;
    }

    /** Scans and fails the test if the gate is breached. */
    public A11yReport assertWithin(String page, A11yGate gate) {
        A11yReport report = scan(page);
        for (A11yReport.Violation v : gate.known(report)) {
            String msg = "Known accessibility issue on '" + page + "' (" + browser + "), not counted: " + v.rule()
                    + " (" + v.impact() + ") " + v.targets();
            log.warn(msg);
            Report.text("Known accessibility issue", msg);
        }
        List<String> breaches = gate.breaches(report);
        if (!breaches.isEmpty()) {
            throw new AssertionError("Accessibility gate failed on '" + page + "' (" + browser + "): "
                    + String.join("; ", breaches));
        }
        return report;
    }

    private void write(A11yReport report) {
        try {
            Files.createDirectories(outputDir);
            String json = JSON.writeValueAsString(report);
            Files.writeString(outputDir.resolve(report.page() + "-" + report.browser() + ".json"), json);
            Report.json("axe-core " + report.page() + " " + report.countsByImpact(), json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String target(CheckedNode node) {
        return target(node.getTarget());
    }

    /**
     * axe gives a node's target as a list: one selector per frame (and a nested list per shadow
     * root) on the way to the element. Joined with {@code " >>> "}; in a page without frames it is
     * just the element's selector, e.g. {@code .tone-warn}. (Until 2026-10-20 the list's
     * {@code toString()} was used, {@code [.tone-warn]}, which no known issue could ever match.)
     */
    static String target(Object t) {
        if (t == null) {
            return "?";
        }
        if (t instanceof java.util.List<?> parts) {
            return parts.stream().map(A11yScanner::target).collect(java.util.stream.Collectors.joining(" >>> "));
        }
        return t.toString();
    }
}
