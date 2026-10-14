package io.github.raktimchandra.testharness.core.config;

/** Thrown when configuration is missing or malformed. Messages say exactly how to fix it. */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
