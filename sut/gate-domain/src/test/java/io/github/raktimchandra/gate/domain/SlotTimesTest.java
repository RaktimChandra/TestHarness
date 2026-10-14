package io.github.raktimchandra.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SlotTimesTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final Instant SLOT = Instant.parse("2026-10-20T04:30:00Z");

    @Test
    void convertsToTerminalLocalTime() {
        OffsetDateTime t = new SlotTimes(KOLKATA, BugSwitches.none()).toTerminalTime(SLOT);
        assertThat(t).hasToString("2026-10-20T10:00+05:30");
        assertThat(t.toInstant()).isEqualTo(SLOT);
    }

    @Test
    void seededBugShiftsByTheOffset() {
        OffsetDateTime t = new SlotTimes(KOLKATA, BugSwitches.of(SeededBug.TIMEZONE_SLOT_TIMES)).toTerminalTime(SLOT);
        assertThat(t).hasToString("2026-10-20T04:30+05:30");
        assertThat(t.toInstant()).isNotEqualTo(SLOT);
    }

    @Test
    void handlesDaylightSavingZones() {
        SlotTimes london = new SlotTimes(ZoneId.of("Europe/London"), BugSwitches.none());
        assertThat(london.toTerminalTime(Instant.parse("2026-07-01T09:00:00Z"))).hasToString("2026-07-01T10:00+01:00");
        assertThat(london.toTerminalTime(Instant.parse("2026-12-01T09:00:00Z"))).hasToString("2026-12-01T09:00Z");
        assertThat(london.terminalZone()).isEqualTo(ZoneId.of("Europe/London"));
    }
}
