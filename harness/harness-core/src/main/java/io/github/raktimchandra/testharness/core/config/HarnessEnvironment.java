package io.github.raktimchandra.testharness.core.config;

import java.util.Locale;

/** Where the suites run. Selects {@code harness-<name>.yml} on top of the defaults. */
public enum HarnessEnvironment {
    /** Developer machine: SUT started in-process, local browsers. */
    LOCAL,
    /** SUT and Selenium Grid in Docker Compose. */
    DOCKER,
    /** Continuous integration: headless, strict secrets. */
    CI;

    public String fileName() {
        return "harness-" + name().toLowerCase(Locale.ROOT) + ".yml";
    }

    public static HarnessEnvironment parse(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Unknown harness environment '" + value + "'; use local, docker or ci");
        }
    }
}
