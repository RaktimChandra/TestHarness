package io.github.raktimchandra.gate.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingRulesTest {

    private static final Instant START = Instant.parse("2026-10-20T10:00:00Z");
    private static final SlotWindow SLOT = new SlotWindow(START, Duration.ofMinutes(30));
    private final BookingRules rules = BookingRules.DEFAULT;

    @Test
    void bookableOnlyBeforeStart() {
        assertThatCode(() -> rules.requireBookable(SLOT, START.minusSeconds(1))).doesNotThrowAnyException();
        assertCode(() -> rules.requireBookable(SLOT, START), ErrorCode.SLOT_IN_PAST);
        assertCode(() -> rules.requireBookable(SLOT, START.plusSeconds(1)), ErrorCode.SLOT_IN_PAST);
    }

    @Test
    void changeAllowedUntilExactlyTheCutoff() {
        Instant cutoff = START.minus(Duration.ofHours(1));
        assertThatCode(() -> rules.requireChangeable(SLOT, cutoff)).doesNotThrowAnyException();
        assertCode(() -> rules.requireChangeable(SLOT, cutoff.plusSeconds(1)), ErrorCode.CHANGE_CUTOFF_PASSED);
    }

    @Test
    void checkInWindowBoundaries() {
        Instant opens = START.minus(Duration.ofHours(2));
        Instant closes = SLOT.end().plus(Duration.ofMinutes(30));
        assertCode(() -> rules.requireCheckInWindow(SLOT, opens.minusSeconds(1)), ErrorCode.CHECKIN_TOO_EARLY);
        assertThatCode(() -> rules.requireCheckInWindow(SLOT, opens)).doesNotThrowAnyException();
        assertThatCode(() -> rules.requireCheckInWindow(SLOT, closes)).doesNotThrowAnyException();
        assertCode(() -> rules.requireCheckInWindow(SLOT, closes.plusSeconds(1)), ErrorCode.CHECKIN_TOO_LATE);
    }

    @Test
    void rejectsNegativeDurations() {
        assertThatThrownBy(() -> new BookingRules(Duration.ofMinutes(-1), Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookingRules(Duration.ZERO, Duration.ofMinutes(-1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookingRules(Duration.ZERO, Duration.ZERO, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slotWindowRejectsNonPositiveLength() {
        assertThatThrownBy(() -> new SlotWindow(START, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlotWindow(START, Duration.ofMinutes(-5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }
}
