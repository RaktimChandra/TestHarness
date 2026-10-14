package io.github.raktimchandra.testharness.core.retry;

import java.time.Duration;
import java.util.Objects;

/** Delay before retry attempt {@code n} (1-based count of failures so far). */
@FunctionalInterface
public interface Backoff {

    Duration delayAfter(int failures);

    static Backoff fixed(Duration delay) {
        Objects.requireNonNull(delay, "delay");
        return failures -> delay;
    }

    /** {@code initial * 2^(failures-1)}, never more than {@code max}. */
    static Backoff exponential(Duration initial, Duration max) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(max, "max");
        return failures -> {
            int shift = Math.min(Math.max(failures - 1, 0), 30);
            long millis = initial.toMillis() << shift;
            return millis < 0 || millis > max.toMillis() ? max : Duration.ofMillis(millis);
        };
    }

    static Backoff none() {
        return failures -> Duration.ZERO;
    }
}
