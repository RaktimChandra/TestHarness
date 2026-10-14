package io.github.raktimchandra.gate.api.web;

import io.github.raktimchandra.gate.api.persistence.BookingRecord;
import io.github.raktimchandra.gate.api.persistence.SlotRecord;
import io.github.raktimchandra.gate.api.persistence.UserRecord;
import io.github.raktimchandra.gate.domain.CapacityPolicy;
import io.github.raktimchandra.gate.domain.SlotTimes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** Maps persistence records to API views, rendering times in the terminal zone. */
@Component
public class Views {

    private final SlotTimes times;
    private final CapacityPolicy capacity;

    public Views(SlotTimes times, CapacityPolicy capacity) {
        this.times = times;
        this.capacity = capacity;
    }

    public Dtos.SlotView slot(SlotRecord s) {
        return new Dtos.SlotView(s.id(), s.lane(), time(s.start()), time(s.end()), s.capacity(), s.occupied(),
                capacity.remaining(s.occupied(), s.capacity()));
    }

    public Dtos.BookingView booking(BookingRecord b) {
        return new Dtos.BookingView(b.reference(), b.status(), b.direction(), b.containerNumber(),
                new Dtos.DriverView(b.driverUsername(), b.driverDisplayName()),
                new Dtos.SlotRef(b.slotId(), b.lane(), time(b.slotStart()), time(b.slotEnd())),
                time(b.createdAt()), time(b.updatedAt()), b.checkedInAt() == null ? null : time(b.checkedInAt()));
    }

    public Dtos.UserView user(UserRecord u) {
        return new Dtos.UserView(u.username(), u.role(), u.displayName(), u.truckPlate());
    }

    /** Every timestamp the API emits has whole-second precision: one format for all clients. */
    public OffsetDateTime time(Instant instant) {
        return times.toTerminalTime(instant.truncatedTo(ChronoUnit.SECONDS));
    }
}
