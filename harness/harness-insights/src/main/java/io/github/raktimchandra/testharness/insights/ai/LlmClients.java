package io.github.raktimchandra.testharness.insights.ai;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.core.config.Secret;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds the configured client, or none. Nothing in the framework requires one.
 *
 * <pre>
 * ai.provider = none | openai | anthropic     (default none)
 * ai.model    = the provider's model name     (required; no default is guessed)
 * ai.base-url = override, e.g. http://localhost:11434/v1 for Ollama
 * HARNESS_AI_API_KEY = the key (environment only; optional for a localhost server)
 * </pre>
 */
public final class LlmClients {

    private LlmClients() {}

    public static Optional<LlmClient> fromConfig(HarnessConfig config) {
        String provider = config.string("ai.provider", "none").toLowerCase(Locale.ROOT);
        if (provider.equals("none")) {
            return Optional.empty();
        }
        HttpLlmClient.Protocol protocol = switch (provider) {
            case "openai" -> HttpLlmClient.Protocol.OPENAI;
            case "anthropic" -> HttpLlmClient.Protocol.ANTHROPIC;
            default -> throw new IllegalArgumentException("ai.provider must be none, openai or anthropic: " + provider);
        };
        String model = config.find("ai.model")
                .orElseThrow(() -> new IllegalArgumentException("ai.model is required when ai.provider is set"));
        URI base = URI.create(config.string("ai.base-url",
                protocol == HttpLlmClient.Protocol.ANTHROPIC ? "https://api.anthropic.com" : "https://api.openai.com/v1"));
        Optional<Secret> key = Optional.ofNullable(System.getenv("HARNESS_AI_API_KEY"))
                .filter(k -> !k.isBlank()).map(k -> new Secret("HARNESS_AI_API_KEY", k));
        boolean local = "localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost());
        if (key.isEmpty() && !local) {
            throw new IllegalArgumentException("HARNESS_AI_API_KEY is not set (needed for " + base + ")");
        }
        return Optional.of(new HttpLlmClient(protocol, base, model, key,
                config.duration("ai.timeout", Duration.ofSeconds(90))));
    }
}
