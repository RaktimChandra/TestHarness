package io.github.raktimchandra.gate.domain;

/**
 * Stable, machine-readable error codes. They appear in the {@code code} field of every
 * API error response, so tests assert on codes rather than on human-readable messages.
 */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    INVALID_CONTAINER_NUMBER(400),
    SLOT_IN_PAST(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    SLOT_FULL(409),
    DUPLICATE_ACTIVE_BOOKING(409),
    INVALID_STATUS_TRANSITION(409),
    CHANGE_CUTOFF_PASSED(409),
    CHECKIN_TOO_EARLY(409),
    CHECKIN_TOO_LATE(409),
    SAME_SLOT(409),
    TOO_MANY_ATTEMPTS(429);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
