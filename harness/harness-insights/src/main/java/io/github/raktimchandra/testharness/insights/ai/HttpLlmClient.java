package io.github.raktimchandra.testharness.insights.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.raktimchandra.testharness.core.config.Secret;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Two wire formats cover most providers:
 * <ul>
 *   <li>{@link Protocol#OPENAI} — {@code POST {base}/chat/completions}: OpenAI and the many
 *       OpenAI-compatible servers (Azure OpenAI proxies, Groq, Together, Ollama, LM Studio)</li>
 *   <li>{@link Protocol#ANTHROPIC} — {@code POST {base}/v1/messages}</li>
 * </ul>
 */
public final class HttpLlmClient implements LlmClient {

    public enum Protocol { OPENAI, ANTHROPIC }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Protocol protocol;
    private final URI base;
    private final String model;
    private final Optional<Secret> key;
    private final Duration timeout;
    private final HttpClient http;

    public HttpLlmClient(Protocol protocol, URI base, String model, Optional<Secret> key, Duration timeout) {
        this.protocol = protocol;
        this.base = base;
        this.model = model;
        this.key = key;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String describe() {
        return protocol.name().toLowerCase() + "/" + model;
    }

    @Override
    public String complete(String system, String user, List<Image> images) {
        HttpRequest.Builder req = HttpRequest.newBuilder().timeout(timeout).header("Content-Type", "application/json");
        String body;
        if (protocol == Protocol.ANTHROPIC) {
            req.uri(base.resolve(path(base, "v1/messages"))).header("anthropic-version", "2023-06-01");
            key.ifPresent(k -> req.header("x-api-key", k.reveal()));
            body = anthropicBody(system, user, images);
        } else {
            req.uri(base.resolve(path(base, "chat/completions")));
            key.ifPresent(k -> req.header("Authorization", "Bearer " + k.reveal()));
            body = openAiBody(system, user, images);
        }
        HttpResponse<String> res;
        try {
            res = http.send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LlmException("Cannot reach " + describe() + " at " + base + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while calling " + describe(), e);
        }
        if (res.statusCode() / 100 != 2) {
            throw new LlmException(describe() + " answered HTTP " + res.statusCode() + ": " + abbreviate(res.body()));
        }
        try {
            JsonNode json = JSON.readTree(res.body());
            JsonNode text = protocol == Protocol.ANTHROPIC ? json.path("content").path(0).path("text")
                    : json.path("choices").path(0).path("message").path("content");
            if (!text.isTextual() || text.asText().isBlank()) {
                throw new LlmException(describe() + " returned no text: " + abbreviate(res.body()));
            }
            return text.asText();
        } catch (IOException e) {
            throw new LlmException(describe() + " returned invalid JSON: " + abbreviate(res.body()), e);
        }
    }

    private String anthropicBody(String system, String user, List<Image> images) {
        ObjectNode root = JSON.createObjectNode().put("model", model).put("max_tokens", 4000).put("system", system);
        ArrayNode content = JSON.createArrayNode();
        for (Image i : images) {
            content.addObject().put("type", "image").putObject("source").put("type", "base64")
                    .put("media_type", i.mediaType()).put("data", Base64.getEncoder().encodeToString(i.data()));
        }
        content.addObject().put("type", "text").put("text", user);
        root.putArray("messages").addObject().put("role", "user").set("content", content);
        return root.toString();
    }

    private String openAiBody(String system, String user, List<Image> images) {
        ObjectNode root = JSON.createObjectNode().put("model", model).put("temperature", 0.2);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        ObjectNode u = messages.addObject().put("role", "user");
        if (images.isEmpty()) {
            u.put("content", user);
        } else {
            ArrayNode content = u.putArray("content");
            content.addObject().put("type", "text").put("text", user);
            for (Image i : images) {
                content.addObject().put("type", "image_url").putObject("image_url").put("url",
                        "data:" + i.mediaType() + ";base64," + Base64.getEncoder().encodeToString(i.data()));
            }
        }
        return root.toString();
    }

    private static String path(URI base, String suffix) {
        String b = base.getPath();
        return (b.endsWith("/") ? b : b + "/") + suffix;
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
