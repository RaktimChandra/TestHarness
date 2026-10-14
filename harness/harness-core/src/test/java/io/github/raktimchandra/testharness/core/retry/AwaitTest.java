package io.github.raktimchandra.testharness.core.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AwaitTest {

    @Test
    void returnsTheFirstValueThatMatches() {
        AtomicInteger n = new AtomicInteger();
        int value = Await.atMost(Duration.ofSeconds(2)).pollEvery(Duration.ofMillis(1))
                .until("counter reaches 3", n::incrementAndGet, v -> v >= 3);
        assertThat(value).isEqualTo(3);
    }

    @Test
    void timesOutWithTheLastObservedValue() {
        assertThatThrownBy(() -> Await.atMost(Duration.ofMillis(50)).pollEvery(Duration.ofMillis(5))
                .until("never", () -> "nope", v -> false))
                .isInstanceOf(Await.AwaitTimeoutException.class)
                .hasMessageContaining("waiting for never (last value: nope)");
    }

    @Test
    void keepsTheLastErrorAsCause() {
        assertThatThrownBy(() -> Await.atMost(Duration.ofMillis(30)).pollEvery(Duration.ofMillis(5))
                .until("db row", () -> {
                    throw new IllegalStateException("no row");
                }))
                .isInstanceOf(Await.AwaitTimeoutException.class)
                .hasRootCauseMessage("no row");
    }

    @Test
    void recoversWhenTheSupplierStopsThrowing() {
        AtomicInteger n = new AtomicInteger();
        Boolean ok = Await.atMost(Duration.ofSeconds(1)).pollEvery(Duration.ofMillis(1)).until("eventually", () -> {
            if (n.incrementAndGet() < 3) {
                throw new IllegalStateException("not yet");
            }
            return true;
        }, Boolean.TRUE::equals);
        assertThat(ok).isTrue();
    }

    @Test
    void interruptionEndsTheWait() {
        Pause interrupted = d -> {
            throw new InterruptedException();
        };
        assertThatThrownBy(() -> Await.atMost(Duration.ofSeconds(5)).withPause(interrupted).until("x", () -> false))
                .hasMessageContaining("Interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }
}
