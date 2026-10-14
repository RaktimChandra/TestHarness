package io.github.raktimchandra.gate.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Renders instants as the terminal's local time with its UTC offset, e.g. 2026-10-15T09:30+05:30. */
public final class SlotTimes {

    private final ZoneId terminalZone;
    private final BugSwitches bugs;

    public SlotTimes(ZoneId terminalZone, BugSwitches bugs) {
        this.terminalZone = terminalZone;
        this.bugs = bugs;
    }

    public OffsetDateTime toTerminalTime(Instant instant) {
        ZoneOffset offset = terminalZone.getRules().getOffset(instant);
        if (bugs.isEnabled(SeededBug.TIMEZONE_SLOT_TIMES)) {
            // Defect: takes the UTC wall-clock time and merely relabels it with the local offset.
            return instant.atOffset(ZoneOffset.UTC).toLocalDateTime().atOffset(offset);
        }
        return instant.atOffset(offset);
    }

    public ZoneId terminalZone() {
        return terminalZone;
    }
}
