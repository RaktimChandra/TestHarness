package io.github.raktimchandra.gate.api.persistence;

import io.github.raktimchandra.gate.domain.BookingDirection;
import io.github.raktimchandra.gate.domain.BookingStatus;
import java.time.Instant;

/** A booking row joined with its driver and slot. */
public record BookingRecord(
        long id,
        String reference,
        long driverId,
        String driverUsername,
        String driverDisplayName,
        long slotId,
        String lane,
        Instant slotStart,
        Instant slotEnd,
        String containerNumber,
        BookingDirection direction,
        BookingStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant checkedInAt) {}
