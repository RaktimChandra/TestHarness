package io.github.raktimchandra.gate.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Time-based booking rules.
 *
 * <ul>
 *   <li>A slot must start in the future to be booked.</li>
 *   <li>Rescheduling or cancelling is allowed until {@code changeCutoff} before the slot starts.</li>
 *   <li>Check-in opens {@code checkInOpensBefore} the slot start and closes
 *       {@code checkInGraceAfterEnd} after the slot ends.</li>
 * </ul>
 */
public record BookingRules(Duration changeCutoff, Duration checkInOpensBefore, Duration checkInGraceAfterEnd) {

    public static final BookingRules DEFAULT =
            new BookingRules(Duration.ofHours(1), Duration.ofHours(2), Duration.ofMinutes(30));

    public BookingRules {
        Objects.requireNonNull(changeCutoff, "changeCutoff");
        Objects.requireNonNull(checkInOpensBefore, "checkInOpensBefore");
        Objects.requireNonNull(checkInGraceAfterEnd, "checkInGraceAfterEnd");
        if (changeCutoff.isNegative() || checkInOpensBefore.isNegative() || checkInGraceAfterEnd.isNegative()) {
            throw new IllegalArgumentException("durations must not be negative");
        }
    }

    public void requireBookable(SlotWindow slot, Instant now) {
        if (!slot.start().isAfter(now)) {
            throw new DomainException(ErrorCode.SLOT_IN_PAST, "Slot has already started");
        }
    }

    public void requireChangeable(SlotWindow slot, Instant now) {
        if (now.isAfter(slot.start().minus(changeCutoff))) {
            throw new DomainException(ErrorCode.CHANGE_CUTOFF_PASSED,
                    "Bookings can only be changed up to " + changeCutoff.toMinutes() + " minutes before the slot");
        }
    }

    public void requireCheckInWindow(SlotWindow slot, Instant now) {
        if (now.isBefore(slot.start().minus(checkInOpensBefore))) {
            throw new DomainException(ErrorCode.CHECKIN_TOO_EARLY,
                    "Check-in opens " + checkInOpensBefore.toMinutes() + " minutes before the slot");
        }
        if (now.isAfter(slot.end().plus(checkInGraceAfterEnd))) {
            throw new DomainException(ErrorCode.CHECKIN_TOO_LATE, "Check-in window for this slot has closed");
        }
    }
}
