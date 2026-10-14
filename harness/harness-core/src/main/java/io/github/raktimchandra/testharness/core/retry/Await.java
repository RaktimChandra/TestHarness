package io.github.raktimchandra.testharness.core.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Polls a condition until it holds or a deadline passes — the non-browser counterpart of an
 * explicit WebDriver wait (e.g. "until the booking row exists in the database").
 */
public final class Await {

    private final Duration timeout;
    private final Duration interval;
    private final Pause pause;

    private Await(Duration timeout, Duration interval, Pause pause) {
        this.timeout = timeout;
        this.interval = interval;
        this.pause = pause;
    }

    public static Await atMost(Duration timeout) {
        return new Await(timeout, Duration.ofMillis(100), Pause.PARK);
    }

    public Await pollEvery(Duration newInterval) {
        return new Await(timeout, newInterval, pause);
    }

    Await withPause(Pause p) {
        return new Await(timeout, interval, p);
    }

    public <T> T until(String description, Supplier<T> value, Predicate<? super T> condition) {
        Objects.requireNonNull(description, "description");
        long deadline = System.nanoTime() + timeout.toNanos();
        T last = null;
        RuntimeException lastError = null;
        while (true) {
            try {
                last = value.get();
                lastError = null;
                if (condition.test(last)) {
                    return last;
                }
            } catch (RuntimeException e) {
                lastError = e;
            }
            if (System.nanoTime() >= deadline) {
                AwaitTimeoutException ex = new AwaitTimeoutException("Timed out after " + timeout.toMillis()
                        + " ms waiting for " + description + " (last value: " + last + ")");
                if (lastError != null) {
                    ex.initCause(lastError);
                }
                throw ex;
            }
            try {
                pause.pause(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AwaitTimeoutException("Interrupted while waiting for " + description);
            }
        }
    }

    public void until(String description, Supplier<Boolean> condition) {
        until(description, condition, Boolean.TRUE::equals);
    }

    /** Raised when a condition does not hold within the timeout. */
    public static class AwaitTimeoutException extends RuntimeException {
        public AwaitTimeoutException(String message) {
            super(message);
        }
    }
}
