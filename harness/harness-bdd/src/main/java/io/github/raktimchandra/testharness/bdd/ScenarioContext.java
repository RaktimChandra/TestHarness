package io.github.raktimchandra.testharness.bdd;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * State shared between the step-definition classes of one scenario. Cucumber's
 * PicoContainer creates a fresh instance per scenario and injects it into every step class
 * that asks for it in its constructor, so scenarios never leak state into each other.
 *
 * <p>Keys are typed, so {@code get(BOOKING_REF)} returns a {@code String} without casts.
 */
public class ScenarioContext {

    /** A typed key; compare by identity of the instance. */
    public static final class Key<T> {
        private final String name;
        private final Class<T> type;

        private Key(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        public static <T> Key<T> of(String name, Class<T> type) {
            return new Key<>(name, type);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Map<Key<?>, Object> values = new HashMap<>();

    public <T> void put(Key<T> key, T value) {
        values.put(key, key.type.cast(Objects.requireNonNull(value, () -> "null value for " + key)));
    }

    public <T> T get(Key<T> key) {
        Object v = values.get(key);
        if (v == null) {
            throw new IllegalStateException("Scenario has no '" + key + "' yet — is a Given step missing?");
        }
        return key.type.cast(v);
    }

    public <T> T getOrDefault(Key<T> key, T fallback) {
        Object v = values.get(key);
        return v == null ? fallback : key.type.cast(v);
    }

    public boolean has(Key<?> key) {
        return values.containsKey(key);
    }
}
