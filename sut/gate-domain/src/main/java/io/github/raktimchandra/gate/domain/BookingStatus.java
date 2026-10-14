package io.github.raktimchandra.gate.domain;

import java.util.Set;

/** Lifecycle of a gate appointment. */
public enum BookingStatus {
    BOOKED,
    CANCELLED,
    CHECKED_IN;

    /** Statuses that occupy a place in the slot. */
    public static final Set<BookingStatus> OCCUPYING = Set.of(BOOKED, CHECKED_IN);

    public boolean canTransitionTo(BookingStatus target) {
        return this == BOOKED && (target == CANCELLED || target == CHECKED_IN);
    }

    public void requireTransitionTo(BookingStatus target) {
        if (!canTransitionTo(target)) {
            throw new DomainException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot move a booking from " + this + " to " + target);
        }
    }
}
