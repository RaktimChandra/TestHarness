package io.github.raktimchandra.testharness.core.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

    private final List<Duration> pauses = new ArrayList<>();
    private final Pause recording = pauses::add;

    @Test
    void succeedsAfterTransientFailuresWithinTheLimit() {
        AtomicInteger calls = new AtomicInteger();
        String result = Retry.of("port check").maxAttempts(3).retryOn(IllegalStateException.class)
                .backoff(Backoff.fixed(Duration.ofMillis(50))).pause(recording).build()
                .call(() -> {
                    if (calls.incrementAndGet() < 3) {
                        throw new IllegalStateException("not yet");
                    }
                    return "up";
                });
        assertThat(result).isEqualTo("up");
        assertThat(calls).hasValue(3);
        assertThat(pauses).containsExactly(Duration.ofMillis(50), Duration.ofMillis(50));
    }

    @Test
    void stopsAtTheLimitAndKeepsEveryFailure() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> Retry.of("token").maxAttempts(4).retryOn(IllegalStateException.class)
                .backoff(Backoff.none()).pause(recording).build()
                .run(() -> {
                    throw new IllegalStateException("fail " + calls.incrementAndGet());
                }))
                .isInstanceOf(RetryExhaustedException.class)
                .hasMessage("token failed after 4 attempts: fail 4")
                .satisfies(e -> {
                    assertThat(((RetryExhaustedException) e).attempts()).isEqualTo(4);
                    assertThat(e.getSuppressed()).hasSize(3);
                });
        assertThat(calls).hasValue(4);
    }

    @Test
    void nonRetryableFailuresAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> Retry.of("x").maxAttempts(5).retryOn(IllegalStateException.class)
                .pause(recording).build()
                .run(() -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("bad input");
                }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(1);
        assertThat(pauses).isEmpty();
    }

    @Test
    void checkedNonRetryableFailuresAreWrapped() {
        assertThatThrownBy(() -> Retry.of("io").retryOn(IllegalStateException.class).pause(recording).build()
                .run(() -> {
                    throw new IOException("disk");
                }))
                .isInstanceOf(RetryExhaustedException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void retryPredicateIsMandatoryAndLimitsAreEnforced() {
        assertThatThrownBy(() -> Retry.of("x").build()).hasMessageContaining("must say which failures to retry");
        assertThatThrownBy(() -> Retry.of("x").maxAttempts(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Retry.of("x").maxAttempts(11)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interruptionStopsRetrying() {
        Pause interrupted = d -> {
            throw new InterruptedException();
        };
        assertThatThrownBy(() -> Retry.of("x").maxAttempts(3).retryOn(e -> true).pause(interrupted).build()
                .run(() -> {
                    throw new UncheckedIOException(new IOException("down"));
                }))
                .isInstanceOf(RetryExhaustedException.class)
                .hasMessageContaining("interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void exponentialBackoffDoublesAndCaps() {
        Backoff b = Backoff.exponential(Duration.ofMillis(100), Duration.ofMillis(1000));
        assertThat(List.of(b.delayAfter(1), b.delayAfter(2), b.delayAfter(3), b.delayAfter(4), b.delayAfter(5),
                b.delayAfter(64))).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200),
                Duration.ofMillis(400), Duration.ofMillis(800), Duration.ofMillis(1000), Duration.ofMillis(1000));
        assertThat(Backoff.none().delayAfter(3)).isZero();
    }

    @Test
    void realPauseWaitsAtLeastTheDuration() throws InterruptedException {
        long start = System.nanoTime();
        Pause.PARK.pause(Duration.ofMillis(30));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(30));
    }
}
