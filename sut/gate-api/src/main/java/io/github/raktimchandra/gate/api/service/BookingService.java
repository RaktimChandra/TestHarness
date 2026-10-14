package io.github.raktimchandra.gate.api.service;

import io.github.raktimchandra.gate.api.config.GateProperties;
import io.github.raktimchandra.gate.api.persistence.BookingRecord;
import io.github.raktimchandra.gate.api.persistence.BookingRepository;
import io.github.raktimchandra.gate.api.persistence.BookingRepository.BookingSearch;
import io.github.raktimchandra.gate.api.persistence.SlotRecord;
import io.github.raktimchandra.gate.api.persistence.SlotRepository;
import io.github.raktimchandra.gate.domain.Actor;
import io.github.raktimchandra.gate.domain.BookingAccessPolicy;
import io.github.raktimchandra.gate.domain.BookingDirection;
import io.github.raktimchandra.gate.domain.BookingReferences;
import io.github.raktimchandra.gate.domain.BookingRules;
import io.github.raktimchandra.gate.domain.BookingStatus;
import io.github.raktimchandra.gate.domain.BugSwitches;
import io.github.raktimchandra.gate.domain.CapacityPolicy;
import io.github.raktimchandra.gate.domain.ContainerNumber;
import io.github.raktimchandra.gate.domain.DomainException;
import io.github.raktimchandra.gate.domain.ErrorCode;
import io.github.raktimchandra.gate.domain.Role;
import io.github.raktimchandra.gate.domain.SeededBug;
import io.github.raktimchandra.gate.domain.SlotWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Booking use cases. Every method runs in one transaction. */
@Service
public class BookingService {

    private static final int MAX_RESULTS = 200;

    private final BookingRepository bookings;
    private final SlotRepository slots;
    private final CapacityPolicy capacity;
    private final BookingAccessPolicy access;
    private final BookingRules rules;
    private final BookingReferences references;
    private final BugSwitches bugs;
    private final Clock clock;
    private final ZoneId zone;
    private final Duration raceWindow;

    public BookingService(BookingRepository bookings, SlotRepository slots, CapacityPolicy capacity,
                          BookingAccessPolicy access, BookingRules rules, BookingReferences references,
                          BugSwitches bugs, Clock clock, GateProperties props) {
        this.bookings = bookings;
        this.slots = slots;
        this.capacity = capacity;
        this.access = access;
        this.rules = rules;
        this.references = references;
        this.bugs = bugs;
        this.clock = clock;
        this.zone = props.terminalZone();
        this.raceWindow = props.raceWindow();
    }

    @Transactional
    public BookingRecord book(Actor driver, long slotId, String rawContainer, BookingDirection direction) {
        if (driver.role() != Role.DRIVER) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only drivers can book appointments");
        }
        ContainerNumber container = bugs.isEnabled(SeededBug.API_SKIPS_CONTAINER_VALIDATION)
                ? ContainerNumber.unchecked(rawContainer)
                : ContainerNumber.parse(rawContainer, bugs);
        SlotRecord slot = claimPlace(slotId);
        rules.requireBookable(slot.window(), clock.instant());

        // References are 8 random Crockford base-32 characters (~1.1e12 values); a collision
        // fails the insert on the unique constraint rather than silently overwriting anything.
        String reference = references.next();
        try {
            bookings.insert(reference, driver.userId(), slot.id(), container.value(), direction, clock.instant());
        } catch (DuplicateKeyException e) {
            if (isOpenContainerConflict(e)) {
                throw new DomainException(ErrorCode.DUPLICATE_ACTIVE_BOOKING,
                        "Container " + container + " already has an open appointment");
            }
            throw e;
        }
        return mustFind(reference);
    }

    @Transactional
    public BookingRecord reschedule(Actor actor, String reference, long newSlotId) {
        BookingRecord booking = mustFind(reference);
        access.requireModify(actor, booking.driverId());
        if (booking.status() != BookingStatus.BOOKED) {
            throw new DomainException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only BOOKED appointments can be rescheduled (this one is " + booking.status() + ")");
        }
        if (booking.slotId() == newSlotId) {
            throw new DomainException(ErrorCode.SAME_SLOT, "The booking is already in that slot");
        }
        Instant now = clock.instant();
        rules.requireChangeable(window(booking), now);
        SlotRecord target = claimPlace(newSlotId);
        rules.requireBookable(target.window(), now);
        bookings.updateSlot(booking.id(), target.id(), now);
        return mustFind(reference);
    }

    @Transactional
    public BookingRecord cancel(Actor actor, String reference) {
        BookingRecord booking = mustFind(reference);
        access.requireModify(actor, booking.driverId());
        booking.status().requireTransitionTo(BookingStatus.CANCELLED);
        rules.requireChangeable(window(booking), clock.instant());
        bookings.updateStatus(booking.id(), BookingStatus.CANCELLED, clock.instant());
        return mustFind(reference);
    }

    @Transactional
    public BookingRecord checkIn(Actor clerk, String reference) {
        access.requireCheckIn(clerk);
        BookingRecord booking = mustFind(reference);
        booking.status().requireTransitionTo(BookingStatus.CHECKED_IN);
        rules.requireCheckInWindow(window(booking), clock.instant());
        bookings.markCheckedIn(booking.id(), clerk.userId(), clock.instant());
        return mustFind(reference);
    }

    @Transactional(readOnly = true)
    public BookingRecord get(Actor actor, String reference) {
        BookingRecord booking = mustFind(reference);
        access.requireView(actor, booking.driverId());
        return booking;
    }

    /** Drivers always see only their own bookings; clerks can search everything. */
    @Transactional(readOnly = true)
    public List<BookingRecord> search(Actor actor, String container, String reference, BookingStatus status,
                                      String driverUsername, LocalDate date) {
        Long driverId = actor.isClerk() ? null : actor.userId();
        Instant from = date == null ? null : date.atStartOfDay(zone).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
        String normalisedContainer = container == null || container.isBlank() ? null : container.strip().toUpperCase();
        String normalisedRef = reference == null || reference.isBlank() ? null : reference.strip().toUpperCase();
        String driver = driverUsername == null || driverUsername.isBlank() ? null : driverUsername.strip();
        return bookings.search(new BookingSearch(driverId, normalisedContainer, normalisedRef, status, driver, from, to,
                MAX_RESULTS));
    }

    /**
     * Checks that the slot has a free place, holding a row lock on the slot so that concurrent
     * bookings for the same slot are serialised. With RACE_ON_LAST_SLOT active the lock is
     * skipped and a short pause widens the check-then-insert window.
     */
    private SlotRecord claimPlace(long slotId) {
        boolean racy = bugs.isEnabled(SeededBug.RACE_ON_LAST_SLOT);
        if (!racy && !slots.lockForUpdate(slotId)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Slot not found");
        }
        SlotRecord slot = slots.findById(slotId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Slot not found"));
        capacity.requireRoom(slot.occupied(), slot.capacity());
        if (racy && !raceWindow.isZero()) {
            LockSupport.parkNanos(raceWindow.toNanos());
        }
        return slot;
    }

    private BookingRecord mustFind(String reference) {
        String ref = reference == null ? "" : reference.strip().toUpperCase();
        return bookings.findByReference(ref)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Booking not found"));
    }

    private static SlotWindow window(BookingRecord b) {
        return new SlotWindow(b.slotStart(),
                Duration.between(b.slotStart(), b.slotEnd()));
    }

    private static boolean isOpenContainerConflict(DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.contains("ux_booking_open_container");
    }
}
