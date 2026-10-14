package io.github.raktimchandra.gate.api.persistence;

import io.github.raktimchandra.gate.domain.SlotWindow;
import java.time.Duration;
import java.time.Instant;

/** A slot row plus the number of places currently occupied. */
public record SlotRecord(long id, String lane, Instant start, Instant end, int capacity, int occupied) {

    public SlotWindow window() {
        return new SlotWindow(start, Duration.between(start, end));
    }
}
