package io.github.raktimchandra.testharness.ui.driver;

import java.util.Locale;

public enum Browser {
    CHROME,
    FIREFOX,
    EDGE;

    public static Browser parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown browser '" + value + "'; use chrome, firefox or edge", e);
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
