package io.github.raktimchandra.testharness.insights.ai;

/** The provider answered with an error or something unusable. The message never contains the key. */
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
