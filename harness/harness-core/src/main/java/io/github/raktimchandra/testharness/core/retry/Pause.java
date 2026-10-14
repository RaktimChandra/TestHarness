package io.github.raktimchandra.testharness.core.retry;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * The only place in the framework that blocks a thread for a fixed time. It exists solely
 * for retry back-off and condition polling; an ArchUnit rule ({@code HarnessArchRules})
 * forbids {@code Thread.sleep} everywhere and allows {@code LockSupport.park*} only here.
 */
@FunctionalInterface
public interface Pause {

    void pause(Duration duration) throws InterruptedException;

    /** Parks the current thread; honours interruption. */
    Pause PARK = duration -> {
        long deadline = System.nanoTime() + duration.toNanos();
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0) {
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) {
                throw new InterruptedException("interrupted while pausing");
            }
        }
    };
}
