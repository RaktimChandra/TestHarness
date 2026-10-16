package io.github.raktimchandra.gate.api.service;

import io.github.raktimchandra.gate.domain.DomainException;
import io.github.raktimchandra.gate.domain.ErrorCode;
import java.time.Duration;

/** A sign-in refused by {@link LoginThrottle}; the handler adds {@code Retry-After}. */
public class LoginThrottledException extends DomainException {

    private final Duration retryAfter;

    public LoginThrottledException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_ATTEMPTS, "Too many failed sign-in attempts. Try again in "
                + minutes(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /** Whole seconds, rounded up, never below one: the value of the Retry-After header. */
    public long retryAfterSeconds() {
        long s = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, s);
    }

    private static String minutes(Duration d) {
        long m = Math.max(1, (d.toSeconds() + 59) / 60);
        return m == 1 ? "a minute" : m + " minutes";
    }
}
