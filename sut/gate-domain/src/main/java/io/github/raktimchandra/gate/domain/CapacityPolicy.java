package io.github.raktimchandra.gate.domain;

/** Decides whether a slot can take one more booking. */
public final class CapacityPolicy {

    private final BugSwitches bugs;

    public CapacityPolicy(BugSwitches bugs) {
        this.bugs = bugs;
    }

    public boolean hasRoom(int activeBookings, int capacity) {
        if (capacity < 0 || activeBookings < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        return bugs.isEnabled(SeededBug.OFF_BY_ONE_CAPACITY)
                ? activeBookings <= capacity
                : activeBookings < capacity;
    }

    public void requireRoom(int activeBookings, int capacity) {
        if (!hasRoom(activeBookings, capacity)) {
            throw new DomainException(ErrorCode.SLOT_FULL,
                    "Slot is full (" + activeBookings + "/" + capacity + " places taken)");
        }
    }

    public int remaining(int activeBookings, int capacity) {
        return Math.max(0, capacity - activeBookings);
    }
}
