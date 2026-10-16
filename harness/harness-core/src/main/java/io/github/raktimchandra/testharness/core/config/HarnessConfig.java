package io.github.raktimchandra.testharness.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Layered, immutable configuration. Later layers override earlier ones:
 *
 * <ol>
 *   <li>{@code harness-defaults.yml}: framework defaults (shipped in harness-core)</li>
 *   <li>{@code harness.yml}: project settings (shipped by the project's suites)</li>
 *   <li>{@code harness-<env>.yml} for the active environment (local, docker, ci)</li>
 *   <li>environment variables: {@code HARNESS_UI_BROWSER} overrides {@code ui.browser}</li>
 *   <li>system properties: {@code -Dharness.ui.browser=firefox}</li>
 * </ol>
 *
 * The environment itself comes from {@code -Dharness.env} or {@code HARNESS_ENV} (default local).
 * Secrets are never read from files: see {@link #secret(String)}.
 */
public final class HarnessConfig {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static volatile HarnessConfig current;

    private final HarnessEnvironment environment;
    private final Map<String, String> values;
    private final Map<String, String> origins;
    private final Function<String, String> env;

    HarnessConfig(HarnessEnvironment environment, Map<String, String> values, Map<String, String> origins,
                  Function<String, String> env) {
        this.environment = environment;
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
        this.origins = Collections.unmodifiableMap(new TreeMap<>(origins));
        this.env = env;
    }

    /** The process-wide configuration, loaded once on first use. */
    public static HarnessConfig get() {
        HarnessConfig c = current;
        if (c == null) {
            synchronized (HarnessConfig.class) {
                c = current;
                if (c == null) {
                    c = load(System.getenv(), System.getProperties(), Thread.currentThread().getContextClassLoader());
                    current = c;
                }
            }
        }
        return c;
    }

    /** Forces the next {@link #get()} to reload (used by tests). */
    public static void reset() {
        current = null;
    }

    public static HarnessConfig load(Map<String, String> envVars, Properties sysProps, ClassLoader loader) {
        String envName = sysProps.getProperty("harness.env", envVars.get("HARNESS_ENV"));
        HarnessEnvironment environment = HarnessEnvironment.parse(envName);
        Map<String, String> values = new TreeMap<>();
        Map<String, String> origins = new TreeMap<>();
        readAll(loader, "harness-defaults.yml", values, origins);
        readAll(loader, "harness.yml", values, origins);
        readAll(loader, environment.fileName(), values, origins);
        for (Map.Entry<String, String> e : envVars.entrySet()) {
            if (e.getKey().startsWith("HARNESS_") && !e.getKey().equals("HARNESS_ENV")) {
                // '_' stands for '.' or '-' (env names cannot hold '-'): HARNESS_UI_NO_SANDBOX sets
                // ui.no-sandbox when that key exists in the files, else ui.no.sandbox.
                String dotted = e.getKey().substring("HARNESS_".length()).toLowerCase(Locale.ROOT).replace('_', '.');
                String key = values.keySet().stream().filter(k -> relaxed(k).equals(dotted)).findFirst().orElse(dotted);
                values.put(key, e.getValue());
                origins.put(key, "env " + e.getKey());
            }
        }
        for (String name : sysProps.stringPropertyNames()) {
            if (name.startsWith("harness.") && !name.equals("harness.env")) {
                String key = name.substring("harness.".length());
                values.put(key, sysProps.getProperty(name));
                origins.put(key, "-D" + name);
            }
        }
        return new HarnessConfig(environment, values, origins, envVars::get);
    }

    public HarnessEnvironment environment() {
        return environment;
    }

    public Optional<String> find(String key) {
        String v = values.get(key);
        if (v == null && key.indexOf('-') >= 0) {
            // set from an environment variable for a key no file declares (see load)
            v = values.get(relaxed(key));
        }
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    public String string(String key) {
        return find(key).orElseThrow(() -> new ConfigException("Missing configuration '" + key
                + "'. Set it in harness-" + environment.name().toLowerCase(Locale.ROOT)
                + ".yml, with -Dharness." + key + "=..., or with env " + envName(key)));
    }

    public String string(String key, String fallback) {
        return find(key).orElse(fallback);
    }

    public int integer(String key, int fallback) {
        return find(key).map(v -> parse(key, v, Integer::parseInt)).orElse(fallback);
    }

    public boolean bool(String key, boolean fallback) {
        return find(key).map(v -> parse(key, v, s -> switch (s.strip().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException(s);
        })).orElse(fallback);
    }

    /** Durations accept {@code 500ms}, {@code 10s}, {@code 2m} or ISO-8601 ({@code PT10S}). */
    public Duration duration(String key, Duration fallback) {
        return find(key).map(v -> parse(key, v, HarnessConfig::parseDuration)).orElse(fallback);
    }

    /**
     * Reads a secret from the environment variable {@code name} only. In the CI environment a
     * missing secret is an error; elsewhere the documented local-only fallback is used.
     */
    public Secret secret(String name, String localFallback) {
        String v = env.apply(name);
        if (v != null && !v.isBlank()) {
            return new Secret(name, v);
        }
        if (environment == HarnessEnvironment.CI || localFallback == null) {
            throw new ConfigException("Secret " + name + " is not set. Export it as an environment variable"
                    + (environment == HarnessEnvironment.CI ? " (required in CI; no fallback is used)." : "."));
        }
        return new Secret(name, localFallback);
    }

    /** Where a key's value came from, for diagnostics and the report's environment page. */
    public String origin(String key) {
        String o = origins.get(key);
        return o != null ? o : origins.getOrDefault(relaxed(key), "unset");
    }

    /** All values (secrets are never stored here), sorted by key. */
    public Map<String, String> asMap() {
        return values;
    }

    static Duration parseDuration(String raw) {
        String s = raw.strip().toLowerCase(Locale.ROOT);
        try {
            if (s.startsWith("pt") || s.startsWith("p")) {
                return Duration.parse(s.toUpperCase(Locale.ROOT));
            }
            if (s.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
            }
            long n = Long.parseLong(s.substring(0, s.length() - 1));
            return switch (s.charAt(s.length() - 1)) {
                case 's' -> Duration.ofSeconds(n);
                case 'm' -> Duration.ofMinutes(n);
                case 'h' -> Duration.ofHours(n);
                default -> throw new IllegalArgumentException(raw);
            };
        } catch (NumberFormatException | DateTimeParseException | StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(raw, e);
        }
    }

    private static <T> T parse(String key, String value, Function<String, T> parser) {
        try {
            return parser.apply(value);
        } catch (RuntimeException e) {
            throw new ConfigException("Configuration '" + key + "' has an invalid value '" + value + "'", e);
        }
    }

    private static String envName(String key) {
        return "HARNESS_" + key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    private static String relaxed(String key) {
        return key.replace('-', '.');
    }

    private static void readAll(ClassLoader loader, String resource, Map<String, String> into,
                                Map<String, String> origins) {
        try {
            Enumeration<URL> urls = loader.getResources(resource);
            // Framework defaults come from jars; project files (target/classes) override them.
            java.util.List<URL> ordered = new java.util.ArrayList<>(Collections.list(urls));
            ordered.sort((a, b) -> Boolean.compare(!isJar(a), !isJar(b)));
            for (URL url : ordered) {
                try (InputStream in = url.openStream()) {
                    JsonNode root = YAML.readTree(in);
                    if (root != null) {
                        flatten("", root, into, origins, resource);
                    }
                }
            }
        } catch (IOException e) {
            throw new ConfigException("Could not read " + resource, e);
        }
    }

    private static boolean isJar(URL url) {
        return "jar".equals(url.getProtocol());
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> into, Map<String, String> origins,
                                String source) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                flatten(prefix.isEmpty() ? f.getKey() : prefix + "." + f.getKey(), f.getValue(), into, origins, source);
            }
        } else if (!node.isNull()) {
            String value = node.isArray() ? joinArray(node) : node.asText();
            into.put(prefix, value);
            origins.put(prefix, source);
        }
    }

    private static String joinArray(JsonNode array) {
        StringBuilder sb = new StringBuilder();
        array.forEach(n -> sb.append(sb.isEmpty() ? "" : ",").append(n.asText()));
        return sb.toString();
    }
}
