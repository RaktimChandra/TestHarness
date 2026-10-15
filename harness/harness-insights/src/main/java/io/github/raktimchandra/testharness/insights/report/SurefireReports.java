package io.github.raktimchandra.testharness.insights.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Reads surefire/failsafe {@code TEST-*.xml} reports: every test case with its outcome. */
public final class SurefireReports {

    public enum Outcome { PASSED, FAILED, ERROR, SKIPPED }

    /**
     * @param module      the module directory the report came from (e.g. {@code suites/api-suite})
     * @param failureType exception class for failures and errors, else null
     */
    public record Case(String module, String className, String name, double seconds, Outcome outcome,
                       String failureType, String message, String stackTrace) {

        public boolean failed() {
            return outcome == Outcome.FAILED || outcome == Outcome.ERROR;
        }

        /** Test key as {@link io.github.raktimchandra.testharness.insights.TestKeys} builds it for Jupiter. */
        public String key() {
            String method = name.contains("(") ? name.substring(0, name.indexOf('(')) : name;
            return className + "#" + method;
        }
    }

    private SurefireReports() {}

    /** Every {@code TEST-*.xml} under any {@code target/surefire-reports} below {@code root}. */
    public static List<Case> read(Path root) {
        List<Case> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")
                    && p.getParent().getFileName().toString().endsWith("-reports")).sorted().toList()) {
                out.addAll(readFile(root, f));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    static List<Case> readFile(Path root, Path file) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(file.toFile());
            Path reportsDir = file.getParent();
            Path moduleDir = reportsDir.getParent() == null ? reportsDir : reportsDir.getParent().getParent();
            String module = moduleDir == null ? "" : root.toAbsolutePath().normalize()
                    .relativize(moduleDir.toAbsolutePath().normalize()).toString().replace('\\', '/');
            List<Case> cases = new ArrayList<>();
            NodeList tcs = doc.getElementsByTagName("testcase");
            for (int i = 0; i < tcs.getLength(); i++) {
                Element tc = (Element) tcs.item(i);
                Outcome outcome = Outcome.PASSED;
                Element detail = null;
                for (String tag : List.of("failure", "error", "skipped")) {
                    NodeList n = tc.getElementsByTagName(tag);
                    if (n.getLength() > 0) {
                        detail = (Element) n.item(0);
                        outcome = switch (tag) {
                            case "failure" -> Outcome.FAILED;
                            case "error" -> Outcome.ERROR;
                            default -> Outcome.SKIPPED;
                        };
                        break;
                    }
                }
                double seconds = tc.getAttribute("time").isBlank() ? 0 : Double.parseDouble(tc.getAttribute("time"));
                cases.add(new Case(module, tc.getAttribute("classname"), tc.getAttribute("name"), seconds, outcome,
                        detail == null || outcome == Outcome.SKIPPED ? null : detail.getAttribute("type"),
                        detail == null ? null : detail.getAttribute("message"),
                        detail == null ? null : detail.getTextContent()));
            }
            return cases;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }
}
