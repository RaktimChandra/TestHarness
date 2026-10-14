package io.github.raktimchandra.testharness.core.retry;

/** All attempts failed. The last failure is the cause; earlier ones are suppressed. */
public class RetryExhaustedException extends RuntimeException {

    private final int attempts;

    public RetryExhaustedException(String operation, int attempts, Throwable last) {
        super(operation + " failed after " + attempts + " attempt" + (attempts == 1 ? "" : "s")
                + ": " + last.getMessage(), last);
        this.attempts = attempts;
    }

    public int attempts() {
        return attempts;
    }
}
