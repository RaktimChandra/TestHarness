package io.github.raktimchandra.testharness.core.config;

import java.util.Objects;

/**
 * A sensitive value. {@link #toString()} never reveals it, so a secret that ends up in a log
 * line, an exception message or an Allure parameter shows as {@code ****}.
 */
public final class Secret {

    private final String name;
    private final String value;

    public Secret(String name, String value) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = Objects.requireNonNull(value, "value");
    }

    public String name() {
        return name;
    }

    /** The real value. Call only at the point of use (an HTTP header, a login form). */
    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return name + "=****";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Secret s && s.name.equals(name) && s.value.equals(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}
