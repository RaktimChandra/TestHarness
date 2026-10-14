package io.github.raktimchandra.testharness.gate.api;

import java.time.OffsetDateTime;
import java.util.List;

/** Typed views of Gate API payloads, as the tests see them (independent of the SUT's classes). */
public final class Models {

    private Models() {}

    public record User(String username, String role, String displayName, String truckPlate) {}

    public record Login(String token, String tokenType, OffsetDateTime expiresAt, User user) {}

    public record Slot(long id, String lane, OffsetDateTime start, OffsetDateTime end, int capacity, int booked,
                       int remaining) {}

    public record Driver(String username, String displayName) {}

    public record SlotRef(long id, String lane, OffsetDateTime start, OffsetDateTime end) {}

    public record Booking(String reference, String status, String direction, String containerNumber, Driver driver,
                          SlotRef slot, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                          OffsetDateTime checkedInAt) {}

    public record Problem(String type, String title, int status, String detail, String instance, String code,
                          List<FieldError> errors) {}

    public record FieldError(String field, String message) {}

    public record CreatedUser(String username, String password, String role) {}

    public record Clock(OffsetDateTime now, long offsetSeconds) {}
}
