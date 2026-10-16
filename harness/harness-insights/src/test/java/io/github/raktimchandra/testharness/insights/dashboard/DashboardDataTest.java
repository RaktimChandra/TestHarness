package io.github.raktimchandra.testharness.insights.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raktimchandra.testharness.insights.history.HistoryJson;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import io.github.raktimchandra.testharness.insights.history.TestResult.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DashboardDataTest {

    @TempDir
    Path repo;

    private static TestResult result(String run, String key, Status status, long ms, int minute) {
        return new TestResult(run, "c1", "main", "api", key, key, status, ms,
                status == Status.FAILED ? "TIMEOUT" : null, null, null, "local",
                Instant.parse("2026-10-01T10:00:00Z").plusSeconds(60L * minute));
    }

    private static Path write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    @Test
    void emptyRepositoryListsEveryMissingSourceAndInventsNothing() {
        JsonNode doc = new DashboardData(repo).build(repo.resolve("out"), List.of());
        assertThat(doc.get("historyResults").asInt()).isZero();
        assertThat(doc.get("runs")).isEmpty();
        assertThat(doc.get("coverage")).isEmpty();
        assertThat(doc.has("mutation")).isFalse();
        assertThat(doc.get("missing").toString()).contains("history:", "coverage:", "mutation:", "a11y:",
                "faultSeeding:", "tia:");
    }

    @Test
    void everySourceIsCollected() throws Exception {
        List<TestResult> h = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String run = "r" + i;
            h.add(result(run, "A#stable", Status.PASSED, 100, i));
            h.add(result(run, "B#flaky", i % 2 == 0 ? Status.PASSED : Status.FAILED, 900, i));
        }
        HistoryJson.append(repo.resolve("suites/api/target/test-history/history.jsonl"), h);
        write(repo.resolve("sut/x/target/site/jacoco/jacoco.csv"), """
                GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
                x,p,A,0,10,1,3,2,8,0,1,0,1
                x,p,B,0,10,0,2,0,10,0,1,0,1
                """);
        write(repo.resolve("sut/x/target/pit-reports/mutations.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                <mutation detected='true' status='KILLED'><mutatedClass>p.A</mutatedClass><mutatedMethod>m</mutatedMethod><lineNumber>3</lineNumber><description>negated</description></mutation>
                <mutation detected='true' status='TIMED_OUT'><mutatedClass>p.A</mutatedClass><mutatedMethod>m</mutatedMethod><lineNumber>4</lineNumber><description>loop</description></mutation>
                <mutation detected='false' status='SURVIVED'><mutatedClass>p.B</mutatedClass><mutatedMethod>n</mutatedMethod><lineNumber>9</lineNumber><description>boundary</description></mutation>
                </mutations>
                """);
        write(repo.resolve("suites/ui/target/a11y/login-chrome.json"), """
                {"page":"login","browser":"chrome","url":"http://x/","violations":[
                 {"rule":"color-contrast","impact":"serious","help":"h","helpUrl":"u","nodes":2,"targets":[]}]}
                """);
        Files.createDirectories(repo.resolve("suites/ui/target/visual/diff/chrome-linux"));
        Files.write(repo.resolve("suites/ui/target/visual/diff/chrome-linux/form-diff.png"), new byte[] {1});
        Files.write(repo.resolve("suites/ui/target/visual/diff/chrome-linux/form-actual.png"), new byte[] {2});
        write(repo.resolve("suites/ui/target/healing/healing.jsonl"), "{\"test\":\"T\",\"rank\":1}\n");
        write(repo.resolve("harness/harness-ui/target/healing/healing.jsonl"), "{\"test\":\"Self\",\"rank\":1}\n");
        write(repo.resolve("experiments/fault-seeding/fault-seeding.json"), "{\"bugs\":[\"X\"]}");
        write(repo.resolve("experiments/tia/tia-experiment.json"), "{\"bugs\":[]}");

        Path out = repo.resolve("dash");
        JsonNode doc = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(
                new DashboardData(repo).build(out, List.of())));

        assertThat(doc.get("historyResults").asInt()).isEqualTo(12);
        assertThat(doc.get("runs")).hasSize(6);
        assertThat(doc.get("runs").get(1).get("passRate").asDouble()).isEqualTo(0.5);
        assertThat(doc.get("flaky").get(0).get("key").asText()).isEqualTo("B#flaky");
        assertThat(doc.get("quarantine").get("tests").get(0).get("key").asText()).isEqualTo("B#flaky");
        assertThat(doc.get("slowest").get(0).get("key").asText()).isEqualTo("B#flaky");
        assertThat(doc.get("failureCauses").get("TIMEOUT").asInt()).isEqualTo(3);

        JsonNode cov = doc.get("coverage").get(0);
        assertThat(cov.get("module").asText()).isEqualTo("sut/x");
        assertThat(cov.get("lineCovered").asInt()).isEqualTo(18);
        assertThat(cov.get("lineTotal").asInt()).isEqualTo(20);
        assertThat(cov.get("branchTotal").asInt()).isEqualTo(6);

        JsonNode mut = doc.get("mutation");
        assertThat(mut.get("total").asInt()).isEqualTo(3);
        assertThat(mut.get("detected").asInt()).isEqualTo(2);
        assertThat(mut.get("survivors").get(0).get("line").asText()).isEqualTo("9");

        JsonNode a11y = doc.get("a11y").get(0);
        assertThat(a11y.get("serious").asInt()).isEqualTo(1);
        assertThat(a11y.get("rules").get(0).get("rule").asText()).isEqualTo("color-contrast");

        JsonNode visual = doc.get("visual").get(0);
        assertThat(visual.get("kind").asText()).isEqualTo("mismatch");
        assertThat(out.resolve(visual.get("image").asText())).exists();
        assertThat(out.resolve(visual.get("actual").asText())).exists();
        assertThat(doc.get("visual")).hasSize(1);

        assertThat(doc.get("healing")).hasSize(1);
        assertThat(doc.get("healing").get(0).get("module").asText()).isEqualTo("suites/ui");
        assertThat(doc.get("faultSeeding").get("bugs").get(0).asText()).isEqualTo("X");
        assertThat(doc.get("missing")).isEmpty();
    }

    @Test
    void theSiteIsThePrebuiltPagePlusItsData() throws Exception {
        Path out = repo.resolve("site");
        Path index = DashboardSite.write(new DashboardData(repo).build(out, List.of()), out);
        assertThat(Files.readString(index)).contains("<script src=\"./data.js\"></script>", "__TESTHARNESS__");
        assertThat(Files.readString(out.resolve("data.js"))).startsWith("window.__TESTHARNESS__ = {");
        assertThat(new ObjectMapper().readTree(out.resolve("data.json").toFile()).has("missing")).isTrue();
    }
}
