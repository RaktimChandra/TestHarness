package io.github.raktimchandra.gate.domain;

/**
 * Object-level authorisation. Clerks can see every booking (they work the gate); drivers may
 * only see and change their own bookings. Only clerks check trucks in.
 */
public final class BookingAccessPolicy {

    private final BugSwitches bugs;

    public BookingAccessPolicy(BugSwitches bugs) {
        this.bugs = bugs;
    }

    public boolean canView(Actor actor, long bookingOwnerId) {
        return actor.isClerk() || ownsOrBugged(actor, bookingOwnerId);
    }

    /** Reschedule and cancel are owner-only actions. */
    public boolean canModify(Actor actor, long bookingOwnerId) {
        return ownsOrBugged(actor, bookingOwnerId);
    }

    public boolean canCheckIn(Actor actor) {
        return actor.isClerk();
    }

    public void requireView(Actor actor, long bookingOwnerId) {
        if (!canView(actor, bookingOwnerId)) {
            // 404 rather than 403: do not reveal that someone else's booking exists.
            throw new DomainException(ErrorCode.NOT_FOUND, "Booking not found");
        }
    }

    public void requireModify(Actor actor, long bookingOwnerId) {
        if (actor.isClerk()) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only the driver who made a booking can change it");
        }
        if (!canModify(actor, bookingOwnerId)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Booking not found");
        }
    }

    public void requireCheckIn(Actor actor) {
        if (!canCheckIn(actor)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only gate clerks can check trucks in");
        }
    }

    private boolean ownsOrBugged(Actor actor, long ownerId) {
        if (actor.userId() == ownerId) {
            return true;
        }
        return actor.role() == Role.DRIVER && bugs.isEnabled(SeededBug.MISSING_AUTHORISATION_CHECK);
    }
}
