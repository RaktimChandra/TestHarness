package io.github.raktimchandra.testharness.insights.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.raktimchandra.testharness.core.config.Secret;
import io.github.raktimchandra.testharness.insights.classify.FailureCause;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    private HttpServer server;
    private final Map<String, String> seen = new ConcurrentHashMap<>();

    private URI stub(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            seen.put("path", ex.getRequestURI().getPath());
            seen.put("auth", String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            seen.put("x-api-key", String.valueOf(ex.getRequestHeaders().getFirst("x-api-key")));
            seen.put("version", String.valueOf(ex.getRequestHeaders().getFirst("anthropic-version")));
            seen.put("body", new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void speaksTheOpenAiChatCompletionsProtocol() throws IOException {
        URI base = stub(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}");
        HttpLlmClient c = new HttpLlmClient(HttpLlmClient.Protocol.OPENAI, base.resolve("/v1"), "m-1",
                Optional.of(new Secret("HARNESS_AI_API_KEY", "sk-test")), Duration.ofSeconds(5));
        assertThat(c.complete("sys", "user text", List.of(new LlmClient.Image("image/png", new byte[] {1, 2}))))
                .isEqualTo("hello");
        assertThat(seen.get("path")).isEqualTo("/v1/chat/completions");
        assertThat(seen.get("auth")).isEqualTo("Bearer sk-test");
        JsonNode body = JSON.readTree(seen.get("body"));
        assertThat(body.get("model").asText()).isEqualTo("m-1");
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(body.at("/messages/1/content/0/text").asText()).isEqualTo("user text");
        assertThat(body.at("/messages/1/content/1/image_url/url").asText()).isEqualTo("data:image/png;base64,AQI=");
    }

    @Test
    void speaksTheAnthropicMessagesProtocol() throws IOException {
        URI base = stub(200, "{\"content\":[{\"type\":\"text\",\"text\":\"bonjour\"}]}");
        HttpLlmClient c = new HttpLlmClient(HttpLlmClient.Protocol.ANTHROPIC, base, "m-2",
                Optional.of(new Secret("HARNESS_AI_API_KEY", "ak-test")), Duration.ofSeconds(5));
        assertThat(c.complete("sys", "hi", List.of())).isEqualTo("bonjour");
        assertThat(seen.get("path")).isEqualTo("/v1/messages");
        assertThat(seen.get("x-api-key")).isEqualTo("ak-test");
        assertThat(seen.get("version")).isEqualTo("2023-06-01");
        JsonNode body = JSON.readTree(seen.get("body"));
        assertThat(body.get("system").asText()).isEqualTo("sys");
        assertThat(body.at("/messages/0/content/0/text").asText()).isEqualTo("hi");
        assertThat(c.describe()).isEqualTo("anthropic/m-2");
    }

    @Test
    void providerErrorsAreReportedWithoutTheKey() throws IOException {
        URI base = stub(401, "{\"error\":\"invalid api key\"}");
        HttpLlmClient c = new HttpLlmClient(HttpLlmClient.Protocol.OPENAI, base, "m",
                Optional.of(new Secret("HARNESS_AI_API_KEY", "sk-secret-value")), Duration.ofSeconds(5));
        assertThatThrownBy(() -> c.complete("s", "u", List.of())).isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 401").hasMessageNotContaining("sk-secret-value");
    }

    @Test
    void noProviderMeansNoClientAndMisconfigurationIsExplained() {
        assertThat(LlmClients.fromConfig(io.github.raktimchandra.testharness.core.config.HarnessConfig.load(
                Map.of(), new java.util.Properties(), getClass().getClassLoader()))).isEmpty();
        java.util.Properties p = new java.util.Properties();
        p.setProperty("harness.ai.provider", "openai");
        assertThatThrownBy(() -> LlmClients.fromConfig(io.github.raktimchandra.testharness.core.config.HarnessConfig
                .load(Map.of(), p, getClass().getClassLoader()))).hasMessageContaining("ai.model is required");
    }

    @Test
    void gherkinDraftsAreBannerMarkedValidatedAndKeptOutOfTheSourceTree() throws IOException {
        Path features = dir.resolve("existing.feature");
        Files.writeString(features, """
                Feature: x
                  Scenario: y
                    Given a gate slot tomorrow at 10:00 with room for 2 trucks
                    When I book that slot for a valid container
                """);
        String[] prompt = new String[1];
        LlmClient fake = new LlmClient() {
            public String complete(String system, String user, List<Image> images) {
                prompt[0] = user;
                return "Here you go:\n```gherkin\n@draft\nFeature: Waitlist\n  Scenario: Join\n"
                        + "    Given a gate slot tomorrow at 10:00 with room for 2 trucks\n"
                        + "    When I join the waitlist\n    Then I am number 1 on the waitlist\n```\n";
            }

            public String describe() {
                return "fake/model";
            }
        };
        GherkinDrafter.Draft d = new GherkinDrafter(fake, dir.resolve("out"))
                .draft("As a driver I want to join a waitlist for a full slot", List.of(features));
        assertThat(prompt[0]).contains("a gate slot tomorrow at 10:00 with room for 2 trucks");
        assertThat(d.parses()).isTrue();
        assertThat(d.scenarios()).isEqualTo(1);
        String saved = Files.readString(d.file());
        assertThat(saved).startsWith("# DRAFT — generated by fake/model").contains("Needs human review")
                .contains("@draft");
        assertThat(d.file().getParent()).isEqualTo(dir.resolve("out"));
    }

    @Test
    void anInvalidDraftIsSavedWithAWarning() throws IOException {
        LlmClient broken = new LlmClient() {
            public String complete(String s, String u, List<Image> i) {
                return "Given a step with no feature or scenario\nFeature: late\n";
            }

            public String describe() {
                return "fake/model";
            }
        };
        GherkinDrafter.Draft d = new GherkinDrafter(broken, dir).draft("story", List.of());
        assertThat(d.parses()).isFalse();
        assertThat(Files.readString(d.file())).contains("# WARNING: this draft is not valid Gherkin");
    }

    @Test
    void failureSummaryWorksWithoutAiAndAddsAMarkedDraftWithIt() throws IOException {
        Path reports = dir.resolve("suites/api-suite/target/surefire-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-x.BookingApiTest.xml"), """
                <testsuite name="x.BookingApiTest" tests="3">
                  <testcase classname="x.BookingApiTest" name="ok" time="0.1"/>
                  <testcase classname="x.BookingApiTest" name="capacity()" time="0.2">
                    <failure type="org.opentest4j.AssertionFailedError" message="Expected HTTP 409 but was 201">at x.BookingApiTest.capacity(BookingApiTest.java:42)</failure>
                  </testcase>
                  <testcase classname="x.BookingApiTest" name="boots()" time="0.2">
                    <error type="java.net.ConnectException" message="Connection refused">at y</error>
                  </testcase>
                </testsuite>
                """);
        FailureExplainer.Result plain = new FailureExplainer(Optional.empty(), false).explain(dir, dir.resolve("o1"));
        assertThat(plain.failures()).isEqualTo(2);
        assertThat(plain.byCause()).containsEntry(FailureCause.ASSERTION, 1).containsEntry(FailureCause.ENVIRONMENT, 1);
        assertThat(plain.usedModel()).isFalse();
        assertThat(Files.readString(plain.file())).contains("Expected HTTP 409 but was 201").contains("AI summary off");

        LlmClient fake = new LlmClient() {
            public String complete(String s, String u, List<Image> i) {
                return "The API suite could not reach the app once; the capacity failure is a product bug.";
            }

            public String describe() {
                return "fake/model";
            }
        };
        FailureExplainer.Result ai = new FailureExplainer(Optional.of(fake), false).explain(dir, dir.resolve("o2"));
        assertThat(ai.usedModel()).isTrue();
        assertThat(Files.readString(ai.file())).contains("DRAFT by fake/model (unverified")
                .contains("capacity failure is a product bug");
    }
}
