package io.github.raktimchandra.testharness.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.insights.history.HistoryJson;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import picocli.CommandLine;

/**
 * The commands that run Maven, serve pages or talk to a model, against a fake repository
 * (-Dharness.cli.root) whose Maven Wrapper only records its arguments.
 */
class CliCommandsTest {

    @TempDir
    Path repo;

    private record Out(int code, String out, String err) {}

    private static Out run(String... args) {
        CommandLine cl = HarnessCli.commandLine();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        int code = cl.execute(args);
        return new Out(code, out.toString(), err.toString());
    }

    @BeforeEach
    void fakeRepository() throws Exception {
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Path sh = repo.resolve("mvnw");
        Files.writeString(sh, """
                #!/bin/sh
                echo "mvnw $*" >> "$(dirname "$0")/mvnw.log"
                case "$*" in *-Preport*) mkdir -p "$(dirname "$0")/target/allure-report" \\
                  && echo "<html>fake allure</html>" > "$(dirname "$0")/target/allure-report/index.html";; esac
                exit 0
                """);
        sh.toFile().setExecutable(true);
        Files.writeString(repo.resolve("mvnw.cmd"), """
                @echo off\r
                echo mvnw %*>>"%~dp0mvnw.log"\r
                echo %* | findstr /C:"-Preport" >nul && (mkdir "%~dp0target\\allure-report" 2>nul & echo ^<html^>fake allure^</html^>>"%~dp0target\\allure-report\\index.html")\r
                exit /b 0\r
                """);
        System.setProperty("harness.cli.root", repo.toString());
    }

    @AfterEach
    void clear() {
        System.clearProperty("harness.cli.root");
        System.clearProperty("harness.ai.provider");
        System.clearProperty("harness.ai.model");
        System.clearProperty("harness.ai.base-url");
        io.github.raktimchandra.testharness.core.config.HarnessConfig.reset();
    }

    private String mvnwLog() throws Exception {
        Path log = repo.resolve("mvnw.log");
        return Files.exists(log) ? Files.readString(log) : "";
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Runs a serving command in the background, fetches its page, then stops it. */
    private static String serveAndFetch(int port, String... args) throws Exception {
        AtomicReference<Out> result = new AtomicReference<>();
        Thread t = new Thread(() -> result.set(run(args)));
        t.start();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try {
            for (int i = 0; i < 100; i++) {
                try {
                    HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                            .build(), HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() == 200) {
                        return r.body();
                    }
                } catch (java.io.IOException notYet) {
                    if (!t.isAlive()) {
                        throw new AssertionError("command ended without serving: " + result.get());
                    }
                }
                Thread.sleep(100);
            }
            throw new AssertionError("nothing served on port " + port);
        } finally {
            t.interrupt();
            t.join(5000);
        }
    }

    @Test
    void rerunFailedSelectsTheFailedTestsAndRunsMavenOnTheirModules() throws Exception {
        assertThat(run("rerun-failed").out()).contains("No failed tests");
        Path reports = repo.resolve("suites/api-suite/target/surefire-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-a.ATest.xml"), """
                <testsuite><testcase classname="a.ATest" name="ok"/>
                <testcase classname="a.ATest" name="bad()"><failure type="x" message="m">s</failure></testcase>
                </testsuite>""");
        Out dry = run("rerun-failed", "--dry-run");
        assertThat(dry.out()).contains("suites/api-suite:", "a.ATest#bad", "verify -pl suites/api-suite -Dharness.select=");
        assertThat(mvnwLog()).isEmpty();
        Out real = run("rerun-failed", "--", "-Dextra.flag=1");
        assertThat(real.code()).as(real.err()).isZero();
        // separate fragments: on Windows the properties arrive quoted (Processes.forPlatform)
        assertThat(mvnwLog()).contains("verify -pl suites/api-suite", "-Dharness.select=",
                "-Dsurefire.failIfNoSpecifiedTests=false", "-Dextra.flag=1");
        assertThat(repo.resolve("target/harness/rerun-failed.txt")).content().contains("a.ATest#bad");
    }

    @Test
    void reportGeneratesWithMavenThenServesTheAllureReport() throws Exception {
        Out missing = run("report", "--no-generate", "--no-browser", "--port", "1");
        assertThat(missing.code()).isNotZero();
        assertThat(missing.err()).contains("No report");
        int port = freePort();
        assertThat(serveAndFetch(port, "report", "--no-browser", "--port", Integer.toString(port))).contains("fake allure");
        assertThat(mvnwLog()).contains("-N -Preport verify");
    }

    @Test
    void dashboardServesTheGeneratedPage() throws Exception {
        int port = freePort();
        String page = serveAndFetch(port, "dashboard", "--out", "target/dashboard", "--serve", "--port",
                Integer.toString(port));
        assertThat(page).contains("<html");
        assertThat(repo.resolve("target/dashboard/data.json")).exists();
    }

    @Test
    void tiaMapsRecordingsAndSelectsByChangedFilesOrGitDiff() throws Exception {
        Path rec = repo.resolve("suites/api-suite/target/tia/api-suite");
        Files.createDirectories(rec);
        Files.write(rec.resolve("t1.exec"), new byte[0]);
        Files.writeString(rec.resolve("index.jsonl"), "{\"key\":\"A#t\",\"exec\":\"t1.exec\",\"durationMs\":5}\n");
        Out map = run("tia", "map", "--commit", "abc");
        assertThat(map.code()).as(map.err()).isZero();
        assertThat(map.out()).contains("1 tests, 0 SUT files covered");

        Out sel = run("tia", "select", "--files", "README.md,pom.xml", "--out", "target/tia/sel.txt");
        assertThat(sel.out()).contains("README.md: documentation", "pom.xml: not mapped", "1 of 1 tests selected (run all)");
        assertThat(repo.resolve("target/tia/sel.txt")).content().contains("A#t");

        Out untested = run("tia", "select", "--files", "sut/gate-api/src/main/java/X.java");
        assertThat(untested.out()).contains("WARNING: changed SUT files that no test executes");

        git("init", "-q", "-b", "main");
        git("add", "-A");
        git("-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base");
        git("checkout", "-qb", "change");
        Files.writeString(repo.resolve("NOTES.md"), "docs only\n");
        git("add", "NOTES.md");
        git("-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "docs");
        Out diff = run("tia", "select", "--base", "main");
        assertThat(diff.code()).as(diff.err()).isZero();
        assertThat(diff.out()).contains("NOTES.md: documentation", "0 of 1 tests selected");
        assertThat(run("tia", "select", "--base", "no-such-ref").code()).isNotZero();
    }

    private void git(String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(repo.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor()).as(out).isZero();
    }

    @Test
    void aiGherkinDraftsThroughAModelAndExplainWorksWithout() throws Exception {
        Files.createDirectories(repo.resolve("suites/bdd-suite/src/test/resources/features"));
        Files.writeString(repo.resolve("suites/bdd-suite/src/test/resources/features/a.feature"),
                "Feature: a\n  Scenario: s\n    Given I am a driver\n");
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            byte[] body = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                    + "\"```gherkin\\n@draft\\nFeature: f\\n  Scenario: s\\n    Given I am a driver\\n```\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            Out none = run("ai", "gherkin", "--story", "As a driver I book");
            assertThat(none.code()).isNotZero();
            assertThat(none.err()).contains("No AI provider configured");
            Path story = repo.resolve("story.md");
            Files.writeString(story, "As a driver, I want to book a slot");
            String[] rest = HarnessCli.applySystemProperties(new String[] {"ai", "-Dharness.ai.provider=openai",
                "-Dharness.ai.model=stub", "-Dharness.ai.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "gherkin", "--story-file", story.toString()});
            Out o = run(rest);
            assertThat(o.code()).as(o.err()).isZero();
            assertThat(o.out()).contains("DRAFT written to", "(1 scenarios, valid Gherkin)");
        } finally {
            server.stop(0);
        }
        System.clearProperty("harness.ai.provider");
        io.github.raktimchandra.testharness.core.config.HarnessConfig.reset();
        Out explain = run("ai", "explain");
        assertThat(explain.code()).as(explain.err()).isZero();
        assertThat(explain.out()).contains("0 failure(s)");
    }

    @Test
    void historyIngestsIntoPostgresAndReadsBackFromIt() throws Exception {
        String url = System.getProperty("harness.db.url");
        PostgreSQLContainer<?> pg = null;
        String user = System.getProperty("harness.db.user", "gate");
        // The CLI reads its password from HARNESS_HISTORY_DB_PASSWORD (default "harness"); the
        // Windows job sets it for its external database.
        if (url == null) {
            pg = new PostgreSQLContainer<>("postgres:16-alpine").withUsername("harness").withPassword("harness");
            pg.start();
            url = pg.getJdbcUrl();
            user = "harness";
        }
        try {
            assertThat(run("history", "flaky").err()).contains("Give --jdbc-url and/or --jsonl");
            String key = "Cli#ingest" + System.nanoTime();
            Path file = repo.resolve("h.jsonl");
            HistoryJson.append(file, List.of(new TestResult("r1", "c" + key, "main", "api-suite", key, key,
                    TestResult.Status.PASSED, 10, null, null, null, "ci", Instant.EPOCH)));
            Out ingest = run("history", "ingest", "--jdbc-url", url, "--db-user", user, file.toString());
            assertThat(ingest.code()).as(ingest.err()).isZero();
            assertThat(ingest.out()).contains("Read 1 results, 1 new");
            Out flaky = run("history", "flaky", "--jdbc-url", url, "--db-user", user);
            assertThat(flaky.out()).contains(key);
        } finally {
            if (pg != null) {
                pg.stop();
            }
        }
    }
}
