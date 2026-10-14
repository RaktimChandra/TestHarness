package io.github.raktimchandra.testharness.api.schema;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One line of {@code schemas/registry.yml}: responses to {@code method path} with a status
 * matching {@code status} (exact, e.g. {@code 201}, or a class, e.g. {@code 4xx}) must
 * conform to {@code schema}. Path templates use {@code {name}} for a single segment.
 */
public record SchemaRule(String method, String path, String status, String schema) {

    public boolean matches(String requestMethod, String requestPath, int statusCode) {
        return method.equalsIgnoreCase(requestMethod)
                && pathPattern().matcher(requestPath).matches()
                && statusMatches(statusCode);
    }

    boolean statusMatches(int code) {
        String s = status.toLowerCase(Locale.ROOT);
        if (s.endsWith("xx")) {
            return code / 100 == s.charAt(0) - '0';
        }
        return Integer.parseInt(s) == code;
    }

    /** Exact statuses beat status classes, so a specific rule can override a generic one. */
    int specificity() {
        return status.toLowerCase(Locale.ROOT).endsWith("xx") ? 0 : 1;
    }

    Pattern pathPattern() {
        String regex = Pattern.quote(path).replaceAll("\\{[^}/]+}", "\\\\E[^/]+\\\\Q");
        return Pattern.compile(regex);
    }
}
