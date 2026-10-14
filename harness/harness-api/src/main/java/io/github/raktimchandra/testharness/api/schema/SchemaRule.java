package io.github.raktimchandra.testharness.api.schema;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One line of {@code schemas/registry.yml}: responses to {@code method path} with a status
 * matching {@code status} (exact, e.g. {@code 201}, or a class, e.g. {@code 4xx}) must
 * conform to {@code schema}. Path templates use {@code {name}} for a single segment; the
 * path {@code **} and the method {@code *} match anything (for catch-all error rules).
 * The schema {@code none} registers a response that is deliberately not JSON (an HTML
 * page), so strict mode knows it was considered rather than forgotten.
 */
public record SchemaRule(String method, String path, String status, String schema) {

    public static final String NONE = "none";

    public boolean matches(String requestMethod, String requestPath, int statusCode) {
        return ("*".equals(method) || method.equalsIgnoreCase(requestMethod))
                && ("**".equals(path) || pathPattern().matcher(requestPath).matches())
                && statusMatches(statusCode);
    }

    boolean statusMatches(int code) {
        String s = status.toLowerCase(Locale.ROOT);
        if (s.endsWith("xx")) {
            return code / 100 == s.charAt(0) - '0';
        }
        return Integer.parseInt(s) == code;
    }

    /**
     * Higher wins: an exact status beats a status class, and a concrete path beats the
     * catch-all, so specific rules always override generic ones.
     */
    public int specificity() {
        int s = status.toLowerCase(Locale.ROOT).endsWith("xx") ? 0 : 2;
        return s + ("**".equals(path) ? 0 : 1);
    }

    Pattern pathPattern() {
        String regex = Pattern.quote(path).replaceAll("\\{[^}/]+}", "\\\\E[^/]+\\\\Q");
        return Pattern.compile(regex);
    }
}
