package io.github.raktimchandra.gate.api.service;

import io.github.raktimchandra.gate.api.config.GateProperties;
import io.github.raktimchandra.gate.api.persistence.SlotRepository;
import io.github.raktimchandra.gate.api.persistence.UserRepository;
import io.github.raktimchandra.gate.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Idempotently creates the demo users and a rolling window of slots (two lanes, every 30
 * minutes between 06:00 and 22:00 terminal time). Safe to run on every start-up.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final List<String> LANES = List.of("L1", "L2");
    static final LocalTime FIRST = LocalTime.of(6, 0);
    static final LocalTime LAST = LocalTime.of(21, 30);

    private final GateProperties props;
    private final UserRepository users;
    private final SlotRepository slots;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoDataSeeder(GateProperties props, UserRepository users, SlotRepository slots, PasswordEncoder encoder,
                          Clock clock) {
        this.props = props;
        this.users = users;
        this.slots = slots;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }
        String hash = encoder.encode(props.seed().password());
        int createdUsers = 0;
        createdUsers += users.insertIfAbsent("driver1", hash, Role.DRIVER, "Anika Rao", "TN 01 AB 1234") ? 1 : 0;
        createdUsers += users.insertIfAbsent("driver2", hash, Role.DRIVER, "Bilal Khan", "KA 05 CD 5678") ? 1 : 0;
        createdUsers += users.insertIfAbsent("driver3", hash, Role.DRIVER, "Chen Wei", "MH 12 EF 9012") ? 1 : 0;
        createdUsers += users.insertIfAbsent("clerk1", hash, Role.CLERK, "Divya Menon", null) ? 1 : 0;
        createdUsers += users.insertIfAbsent("clerk2", hash, Role.CLERK, "Elias Fernandes", null) ? 1 : 0;

        int createdSlots = 0;
        LocalDate today = LocalDate.now(clock.withZone(props.terminalZone()));
        for (int d = 0; d < props.seed().days(); d++) {
            LocalDate day = today.plusDays(d);
            for (LocalTime t = FIRST; !t.isAfter(LAST); t = t.plus(props.slotLength())) {
                ZonedDateTime start = ZonedDateTime.of(day, t, props.terminalZone());
                for (String lane : LANES) {
                    if (slots.insertIfAbsent(lane, start.toInstant(), start.plus(props.slotLength()).toInstant(),
                            props.seed().capacity())) {
                        createdSlots++;
                    }
                }
                if (t.equals(LAST)) {
                    break;
                }
            }
        }
        log.info("Demo data ready: {} users and {} slots created", createdUsers, createdSlots);
    }
}
