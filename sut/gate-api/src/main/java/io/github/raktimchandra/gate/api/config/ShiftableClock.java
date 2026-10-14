package io.github.raktimchandra.gate.api.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that tests can move forwards or backwards (via the test-support API) so that
 * time-dependent rules such as the check-in window can be exercised without waiting.
 */
public final class ShiftableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public ShiftableClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private ShiftableClock(Clock base, AtomicReference<Duration> offset) {
        this.base = base;
        this.offset = offset;
    }

    public Duration offset() {
        return offset.get();
    }

    public void setOffset(Duration newOffset) {
        offset.set(newOffset);
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ShiftableClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
