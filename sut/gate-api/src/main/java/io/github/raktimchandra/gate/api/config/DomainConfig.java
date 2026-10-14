package io.github.raktimchandra.gate.api.config;

import io.github.raktimchandra.gate.domain.BookingAccessPolicy;
import io.github.raktimchandra.gate.domain.BookingReferences;
import io.github.raktimchandra.gate.domain.BookingRules;
import io.github.raktimchandra.gate.domain.CapacityPolicy;
import io.github.raktimchandra.gate.domain.MutableBugSwitches;
import io.github.raktimchandra.gate.domain.SlotTimes;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain rules into Spring. */
@Configuration(proxyBeanMethods = false)
public class DomainConfig {

    private static final Logger log = LoggerFactory.getLogger(DomainConfig.class);

    @Bean
    MutableBugSwitches bugSwitches(GateProperties props) {
        if (!props.seededBugs().isEmpty()) {
            log.warn("Starting with SEEDED BUGS active: {}", props.seededBugs());
        }
        return new MutableBugSwitches(props.seededBugs());
    }

    @Bean
    ShiftableClock clock() {
        return new ShiftableClock(Clock.systemUTC());
    }

    @Bean
    CapacityPolicy capacityPolicy(MutableBugSwitches bugs) {
        return new CapacityPolicy(bugs);
    }

    @Bean
    BookingAccessPolicy bookingAccessPolicy(MutableBugSwitches bugs) {
        return new BookingAccessPolicy(bugs);
    }

    @Bean
    SlotTimes slotTimes(GateProperties props, MutableBugSwitches bugs) {
        return new SlotTimes(props.terminalZone(), bugs);
    }

    @Bean
    BookingRules bookingRules() {
        return BookingRules.DEFAULT;
    }

    @Bean
    BookingReferences bookingReferences() {
        return new BookingReferences();
    }
}
