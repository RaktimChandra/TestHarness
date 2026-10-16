package io.github.raktimchandra.testharness.insights.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.raktimchandra.testharness.insights.history.Flakiness;
import io.github.raktimchandra.testharness.insights.history.HistoryJson;
import io.github.raktimchandra.testharness.insights.history.Quarantine;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Collects everything the quality dashboard shows into one JSON document, from files the
 * build and the experiments already produce. Nothing is estimated: a section with no source
 * data is left empty and listed under {@code missing}, and the dashboard says so.
 *
 * <p>Sources (relative to the repository root): history ({@code **}{@code /target/test-history/*.jsonl},
 * {@code experiments/**}{@code /history.jsonl}, or extra results passed in), {@code quarantine.json},
 * JaCoCo {@code target/site/jacoco/jacoco.csv} per module, PIT {@code mutations.xml}, axe JSON in
 * {@code target/a11y}, visual candidates and diffs in {@code target/visual}, healing records,
 * {@code experiments/fault-seeding/fault-seeding.json} and {@code experiments/tia/tia-experiment.json}.
 */
public final class DashboardData {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);
    private static final List<String> SKIP_DIRS = List.of(".git", "node_modules", ".mvn", "dist");

    private final Path root;
    private final List<String> missing = new ArrayList<>();

    public DashboardData(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Builds the document and copies referenced images into {@code outDir}.
     *
     * @param extraHistory results from PostgreSQL or elsewhere, added to the files found
     */
    public ObjectNode build(Path outDir, List<TestResult> extraHistory) {
        ObjectNode doc = JSON.createObjectNode();
        doc.put("generated", Instant.now().toString());
        List<TestResult> history = new ArrayList<>(extraHistory);
        find(p -> p.toString().endsWith(".jsonl") && (p.getParent().getFileName().toString().equals("test-history")
                || p.getFileName().toString().equals("history.jsonl"))).forEach(f -> history.addAll(HistoryJson.read(f)));
        Map<String, TestResult> unique = new LinkedHashMap<>();
        history.forEach(r -> unique.putIfAbsent(r.runId() + "|" + r.suite() + "|" + r.caseId(), r));
        List<TestResult> h = new ArrayList<>(unique.values());
        history(doc, h);
        Path q = root.resolve("quarantine.json");
        doc.set("quarantine", Files.exists(q) ? JSON.valueToTree(Quarantine.read(q))
                : JSON.valueToTree(Quarantine.from(h, Flakiness.Policy.DEFAULT, Instant.now())));
        doc.put("quarantineSource", Files.exists(q) ? "quarantine.json" : "computed from history");
        coverage(doc);
        mutation(doc);
        a11y(doc);
        visual(doc, outDir);
        healing(doc);
        passthrough(doc, "faultSeeding", "experiments/fault-seeding/fault-seeding.json");
        passthrough(doc, "tia", "experiments/tia/tia-experiment.json");
        ArrayNode m = doc.putArray("missing");
        missing.forEach(m::add);
        return doc;
    }

    public static void write(ObjectNode doc, Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, JSON.writeValueAsString(doc), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- history ---------------------------------------------------------------------------
    private void history(ObjectNode doc, List<TestResult> h) {
        doc.put("historyResults", h.size());
        if (h.isEmpty()) {
            missing.add("history: no target/test-history or experiments/**/history.jsonl files");
        }
        Map<String, List<TestResult>> byRun = h.stream().collect(Collectors.groupingBy(TestResult::runId,
                LinkedHashMap::new, Collectors.toList()));
        List<ObjectNode> runs = new ArrayList<>();
        byRun.forEach((run, rs) -> {
            ObjectNode o = JSON.createObjectNode();
            long passed = rs.stream().filter(r -> r.status() == TestResult.Status.PASSED).count();
            long failed = rs.stream().filter(r -> r.status() == TestResult.Status.FAILED).count();
            long skipped = rs.size() - passed - failed;
            o.put("runId", run);
            o.put("startedAt", rs.stream().map(TestResult::startedAt).min(Comparator.naturalOrder()).orElseThrow()
                    .toString());
            o.put("commit", rs.get(0).commitSha());
            o.put("suites", rs.stream().map(TestResult::suite).distinct().sorted().collect(Collectors.joining(", ")));
            o.put("passed", passed);
            o.put("failed", failed);
            o.put("skipped", skipped);
            o.put("passRate", passed + failed == 0 ? 0 : (double) passed / (passed + failed));
            runs.add(o);
        });
        runs.sort(Comparator.comparing(o -> o.get("startedAt").asText()));
        doc.putArray("runs").addAll(runs);

        List<Flakiness.Score> scores = Flakiness.scoreAll(h);
        doc.set("flaky", JSON.valueToTree(scores.stream().filter(s -> s.flips() > 0).limit(20).toList()));
        doc.set("slowest", JSON.valueToTree(scores.stream()
                .sorted(Comparator.comparingLong(Flakiness.Score::meanDurationMs).reversed()).limit(10)
                .map(s -> Map.of("key", s.key(), "displayName", s.displayName(), "meanMs", s.meanDurationMs(),
                        "runs", s.runs()))
                .toList()));
        Map<String, Long> causes = h.stream().filter(r -> r.status() == TestResult.Status.FAILED)
                .collect(Collectors.groupingBy(r -> r.failureCause() == null ? "UNKNOWN" : r.failureCause(),
                        TreeMap::new, Collectors.counting()));
        doc.set("failureCauses", JSON.valueToTree(causes));
    }

    // --- coverage --------------------------------------------------------------------------
    private void coverage(ObjectNode doc) {
        ArrayNode out = doc.putArray("coverage");
        for (Path csv : find(p -> p.getFileName().toString().equals("jacoco.csv"))) {
            try {
                List<String> lines = Files.readAllLines(csv);
                List<String> head = List.of(lines.get(0).split(","));
                long lm = 0, lc = 0, bm = 0, bc = 0;
                for (String line : lines.subList(1, lines.size())) {
                    String[] c = line.split(",");
                    lm += Long.parseLong(c[head.indexOf("LINE_MISSED")]);
                    lc += Long.parseLong(c[head.indexOf("LINE_COVERED")]);
                    bm += Long.parseLong(c[head.indexOf("BRANCH_MISSED")]);
                    bc += Long.parseLong(c[head.indexOf("BRANCH_COVERED")]);
                }
                if (lm + lc == 0) {
                    continue;
                }
                Path module = csv.getParent().getParent().getParent().getParent();
                out.addObject().put("module", rel(module)).put("lineCovered", lc).put("lineTotal", lc + lm)
                        .put("branchCovered", bc).put("branchTotal", bc + bm);
            } catch (IOException | RuntimeException e) {
                missing.add("coverage: unreadable " + rel(csv));
            }
        }
        if (out.isEmpty()) {
            missing.add("coverage: no target/site/jacoco/jacoco.csv (run mvnw verify)");
        }
    }

    // --- mutation --------------------------------------------------------------------------
    private void mutation(ObjectNode doc) {
        List<Path> reports = find(p -> p.getFileName().toString().equals("mutations.xml"));
        if (reports.isEmpty()) {
            missing.add("mutation: no pit-reports/mutations.xml (run mvnw -Pmutation verify -pl sut/gate-domain)");
            return;
        }
        Path f = reports.get(0);
        try {
            Document d = parse(f);
            NodeList ms = d.getElementsByTagName("mutation");
            Map<String, Integer> counts = new TreeMap<>();
            ArrayNode survivors = JSON.createArrayNode();
            for (int i = 0; i < ms.getLength(); i++) {
                Element m = (Element) ms.item(i);
                String status = m.getAttribute("status");
                counts.merge(status, 1, Integer::sum);
                if (status.equals("SURVIVED") || status.equals("NO_COVERAGE")) {
                    survivors.addObject().put("status", status).put("class", text(m, "mutatedClass"))
                            .put("method", text(m, "mutatedMethod")).put("line", text(m, "lineNumber"))
                            .put("description", text(m, "description"));
                }
            }
            int total = ms.getLength();
            int detected = counts.getOrDefault("KILLED", 0) + counts.getOrDefault("TIMED_OUT", 0)
                    + counts.getOrDefault("MEMORY_ERROR", 0);
            ObjectNode o = doc.putObject("mutation");
            o.put("module", rel(f.getParent().getParent().getParent())).put("total", total).put("detected", detected)
                    .put("score", total == 0 ? 0 : (double) detected / total);
            o.set("byStatus", JSON.valueToTree(counts));
            o.set("survivors", survivors);
        } catch (RuntimeException e) {
            missing.add("mutation: unreadable " + rel(f));
        }
    }

    // --- accessibility ---------------------------------------------------------------------
    private void a11y(ObjectNode doc) {
        ArrayNode out = doc.putArray("a11y");
        for (Path f : find(p -> p.toString().endsWith(".json") && p.getParent().getFileName().toString().equals("a11y")
                && p.getParent().getParent().getFileName().toString().equals("target"))) {
            try {
                JsonNode r = JSON.readTree(f.toFile());
                ObjectNode o = out.addObject();
                o.put("page", r.path("page").asText(f.getFileName().toString()));
                o.put("browser", r.path("browser").asText(""));
                o.put("url", r.path("url").asText(""));
                Map<String, Integer> impact = new TreeMap<>();
                ArrayNode rules = o.putArray("rules");
                for (JsonNode v : r.path("violations")) {
                    impact.merge(v.path("impact").asText("unknown"), 1, Integer::sum);
                    rules.addObject().put("rule", v.path("rule").asText()).put("impact", v.path("impact").asText())
                            .put("nodes", v.path("nodes").asInt()).put("help", v.path("help").asText())
                            .put("helpUrl", v.path("helpUrl").asText());
                }
                for (String k : List.of("critical", "serious", "moderate", "minor")) {
                    o.put(k, impact.getOrDefault(k, 0));
                }
            } catch (IOException e) {
                missing.add("a11y: unreadable " + rel(f));
            }
        }
        if (out.isEmpty()) {
            missing.add("a11y: no target/a11y/*.json (run the UI suite)");
        }
    }

    // --- visual ----------------------------------------------------------------------------
    private void visual(ObjectNode doc, Path outDir) {
        ArrayNode out = doc.putArray("visual");
        List<Path> pngs = find(p -> p.toString().endsWith(".png") && p.toString().replace('\\', '/').contains("/target/visual/"));
        for (Path png : pngs) {
            String path = png.toString().replace('\\', '/');
            String name = png.getFileName().toString().replace(".png", "");
            boolean candidate = path.contains("/target/visual/new/");
            boolean diff = path.contains("/target/visual/diff/") && name.endsWith("-diff");
            if (!candidate && !diff) {
                continue;
            }
            String platform = png.getParent().getFileName().toString();
            String base = diff ? name.substring(0, name.length() - "-diff".length()) : name;
            String target = "visual/" + platform + "/" + png.getFileName();
            copy(png, outDir.resolve(target));
            ObjectNode o = out.addObject().put("name", base).put("platform", platform)
                    .put("kind", candidate ? "new-baseline" : "mismatch").put("image", target);
            if (diff) {
                Path actual = png.resolveSibling(base + "-actual.png");
                if (Files.exists(actual)) {
                    String t = "visual/" + platform + "/" + actual.getFileName();
                    copy(actual, outDir.resolve(t));
                    o.put("actual", t);
                }
            }
        }
    }

    // --- healing ---------------------------------------------------------------------------
    private void healing(ObjectNode doc) {
        ArrayNode out = doc.putArray("healing");
        // The framework's own self-tests heal on purpose; only suites' healing is a signal.
        for (Path f : find(p -> p.getFileName().toString().equals("healing.jsonl") && !rel(p).startsWith("harness/"))) {
            try {
                for (String line : Files.readAllLines(f)) {
                    if (!line.isBlank()) {
                        ((ObjectNode) out.addObject().setAll((ObjectNode) JSON.readTree(line))).put("module",
                                rel(f.getParent().getParent().getParent()));
                    }
                }
            } catch (IOException e) {
                missing.add("healing: unreadable " + rel(f));
            }
        }
    }

    private void passthrough(ObjectNode doc, String field, String path) {
        Path f = root.resolve(path);
        if (!Files.exists(f)) {
            missing.add(field + ": no " + path);
            return;
        }
        try {
            doc.set(field, JSON.readTree(f.toFile()));
        } catch (IOException e) {
            missing.add(field + ": unreadable " + path);
        }
    }

    // --- helpers ---------------------------------------------------------------------------
    private List<Path> find(Predicate<Path> match) {
        List<Path> out = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return SKIP_DIRS.contains(dir.getFileName() == null ? "" : dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (match.test(file)) {
                        out.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private String rel(Path p) {
        return root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static void copy(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Document parse(Path f) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return dbf.newDocumentBuilder().parse(f.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + f + ": " + e.getMessage(), e);
        }
    }

    private static String text(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent();
    }
}
