package io.github.raktimchandra.gate.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** A gate time slot: a start instant and a length. */
public record SlotWindow(Instant start, Duration length) {

    public SlotWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(length, "length");
        if (length.isZero() || length.isNegative()) {
            throw new IllegalArgumentException("slot length must be positive");
        }
    }

    public Instant end() {
        return start.plus(length);
    }
}
