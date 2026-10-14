package io.github.raktimchandra.gate.api.web;

import io.github.raktimchandra.gate.domain.BookingDirection;
import io.github.raktimchandra.gate.domain.BookingStatus;
import io.github.raktimchandra.gate.domain.Role;
import io.github.raktimchandra.gate.domain.SeededBug;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Request and response bodies. Times are ISO-8601 with the terminal's UTC offset. */
public final class Dtos {

    private Dtos() {}

    public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {}

    public record UserView(String username, Role role, String displayName, String truckPlate) {}

    public record LoginResponse(String token, String tokenType, OffsetDateTime expiresAt, UserView user) {}

    public record CreateSlotRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,12}", message = "must be 1-12 upper-case letters, digits or '-'")
            String lane,
            @NotNull OffsetDateTime start,
            @Min(5) @Max(240) Integer lengthMinutes,
            @NotNull @Min(1) @Max(50) Integer capacity) {}

    public record SlotView(long id, String lane, OffsetDateTime start, OffsetDateTime end, int capacity, int booked,
                           int remaining) {}

    public record CreateBookingRequest(
            @NotNull @Min(1) Long slotId,
            @NotBlank @Size(max = 32) String containerNumber,
            @NotNull BookingDirection direction) {}

    public record RescheduleRequest(@NotNull @Min(1) Long slotId) {}

    public record DriverView(String username, String displayName) {}

    public record SlotRef(long id, String lane, OffsetDateTime start, OffsetDateTime end) {}

    public record BookingView(String reference, BookingStatus status, BookingDirection direction,
                              String containerNumber, DriverView driver, SlotRef slot, OffsetDateTime createdAt,
                              OffsetDateTime updatedAt, OffsetDateTime checkedInAt) {}

    public record BugsView(List<SeededBug> active, List<BugInfo> available) {}

    public record BugInfo(SeededBug name, String description, String location) {}

    public record SetBugsRequest(@NotNull List<SeededBug> active) {}

    public record ClockView(OffsetDateTime now, long offsetSeconds) {}

    public record SetClockRequest(@NotNull Long offsetSeconds) {}

    public record CreateUserRequest(
            @NotNull Role role,
            @Pattern(regexp = "[a-z0-9._-]{3,64}", message = "must be 3-64 of a-z, 0-9, '.', '_' or '-'")
            String username) {}

    public record CreatedUser(String username, String password, Role role) {}
}
