package io.github.raktimchandra.gate.api.service;

import io.github.raktimchandra.gate.api.config.GateProperties;
import io.github.raktimchandra.gate.api.persistence.SlotRecord;
import io.github.raktimchandra.gate.api.persistence.SlotRepository;
import io.github.raktimchandra.gate.domain.DomainException;
import io.github.raktimchandra.gate.domain.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotService {

    private final SlotRepository slots;
    private final Clock clock;
    private final ZoneId zone;
    private final Duration defaultLength;

    public SlotService(SlotRepository slots, Clock clock, GateProperties props) {
        this.slots = slots;
        this.clock = clock;
        this.zone = props.terminalZone();
        this.defaultLength = props.slotLength();
    }

    @Transactional
    public SlotRecord create(String lane, Instant start, Integer lengthMinutes, int capacity) {
        if (!start.isAfter(clock.instant())) {
            throw new DomainException(ErrorCode.SLOT_IN_PAST, "A new slot must start in the future");
        }
        Duration length = lengthMinutes == null ? defaultLength : Duration.ofMinutes(lengthMinutes);
        try {
            long id = slots.insert(lane, start, start.plus(length), capacity);
            return get(id);
        } catch (DuplicateKeyException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Lane " + lane + " already has a slot starting at that time");
        }
    }

    @Transactional(readOnly = true)
    public SlotRecord get(long id) {
        return slots.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Slot not found"));
    }

    /** Slots starting on {@code date} in the terminal's time zone. */
    @Transactional(readOnly = true)
    public List<SlotRecord> onDate(LocalDate date, String lane) {
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        return slots.findBetween(from, to, lane);
    }
}
