package io.github.raktimchandra.testharness.core.retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded retry for <em>infrastructure</em> operations (waiting for a container port, a
 * token endpoint during start-up). It is deliberately not offered as a way to re-run
 * flaky assertions: flaky tests are measured and quarantined, never silently retried.
 *
 * <p>Every policy has an explicit attempt limit (1..{@value #MAX_ATTEMPTS_CAP}) and an
 * explicit predicate for which failures are retryable; anything else fails immediately.
 */
public final class Retry {

    public static final int MAX_ATTEMPTS_CAP = 10;
    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    private final String operation;
    private final int maxAttempts;
    private final Backoff backoff;
    private final Predicate<Throwable> retryOn;
    private final Pause pause;

    private Retry(Builder b) {
        this.operation = b.operation;
        this.maxAttempts = b.maxAttempts;
        this.backoff = b.backoff;
        this.retryOn = b.retryOn;
        this.pause = b.pause;
    }

    public static Builder of(String operation) {
        return new Builder(operation);
    }

    public <T> T call(Callable<T> action) {
        List<Throwable> failures = new ArrayList<>();
        for (int attempt = 1; ; attempt++) {
            try {
                T result = action.call();
                if (attempt > 1) {
                    log.info("{} succeeded on attempt {}/{}", operation, attempt, maxAttempts);
                }
                return result;
            } catch (Exception e) {
                if (!retryOn.test(e)) {
                    throw e instanceof RuntimeException re ? re : new RetryExhaustedException(operation, attempt, e);
                }
                failures.add(e);
                if (attempt >= maxAttempts) {
                    RetryExhaustedException ex = new RetryExhaustedException(operation, attempt, e);
                    failures.subList(0, failures.size() - 1).forEach(ex::addSuppressed);
                    throw ex;
                }
                Duration delay = backoff.delayAfter(attempt);
                log.warn("{} failed (attempt {}/{}), retrying in {} ms: {}", operation, attempt, maxAttempts,
                        delay.toMillis(), e.toString());
                try {
                    pause.pause(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException(operation + " (interrupted)", attempt, e);
                }
            }
        }
    }

    public void run(ThrowingRunnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static final class Builder {
        private final String operation;
        private int maxAttempts = 3;
        private Backoff backoff = Backoff.exponential(Duration.ofMillis(200), Duration.ofSeconds(2));
        private Predicate<Throwable> retryOn;
        private Pause pause = Pause.PARK;

        private Builder(String operation) {
            this.operation = Objects.requireNonNull(operation, "operation");
        }

        public Builder maxAttempts(int attempts) {
            if (attempts < 1 || attempts > MAX_ATTEMPTS_CAP) {
                throw new IllegalArgumentException("maxAttempts must be between 1 and " + MAX_ATTEMPTS_CAP);
            }
            this.maxAttempts = attempts;
            return this;
        }

        public Builder backoff(Backoff backoff) {
            this.backoff = Objects.requireNonNull(backoff, "backoff");
            return this;
        }

        /** Required: which failures are worth retrying. */
        public Builder retryOn(Predicate<Throwable> predicate) {
            this.retryOn = Objects.requireNonNull(predicate, "predicate");
            return this;
        }

        @SafeVarargs
        public final Builder retryOn(Class<? extends Throwable>... types) {
            return retryOn(t -> {
                for (Class<? extends Throwable> type : types) {
                    if (type.isInstance(t)) {
                        return true;
                    }
                }
                return false;
            });
        }

        Builder pause(Pause pause) {
            this.pause = Objects.requireNonNull(pause, "pause");
            return this;
        }

        public Retry build() {
            if (retryOn == null) {
                throw new IllegalStateException("Retry for '" + operation + "' must say which failures to retry");
            }
            return new Retry(this);
        }
    }
}
