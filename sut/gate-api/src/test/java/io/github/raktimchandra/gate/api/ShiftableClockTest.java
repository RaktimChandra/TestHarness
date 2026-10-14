package io.github.raktimchandra.gate.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.gate.api.config.ShiftableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ShiftableClockTest {

    private static final Instant T0 = Instant.parse("2026-10-20T00:00:00Z");

    @Test
    void appliesTheOffsetAndSharesItAcrossZones() {
        ShiftableClock clock = new ShiftableClock(Clock.fixed(T0, ZoneOffset.UTC));
        Clock kolkata = clock.withZone(ZoneId.of("Asia/Kolkata"));
        clock.setOffset(Duration.ofHours(3));
        assertThat(clock.instant()).isEqualTo(T0.plus(Duration.ofHours(3)));
        assertThat(kolkata.instant()).isEqualTo(T0.plus(Duration.ofHours(3)));
        assertThat(kolkata.getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(clock.offset()).isEqualTo(Duration.ofHours(3));
    }
}
