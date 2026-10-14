package io.github.raktimchandra.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LifecycleAndSwitchesTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
        "BOOKED,CANCELLED,true", "BOOKED,CHECKED_IN,true", "BOOKED,BOOKED,false",
        "CANCELLED,BOOKED,false", "CANCELLED,CHECKED_IN,false", "CHECKED_IN,CANCELLED,false"
    })
    void statusTransitions(BookingStatus from, BookingStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        if (!allowed) {
            assertThatThrownBy(() -> from.requireTransitionTo(to))
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        } else {
            from.requireTransitionTo(to);
        }
    }

    @Test
    void occupyingStatuses() {
        assertThat(BookingStatus.OCCUPYING).containsExactlyInAnyOrder(BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
    }

    @Test
    void mutableSwitchesReplaceAtomically() {
        MutableBugSwitches switches = new MutableBugSwitches(List.of(SeededBug.OFF_BY_ONE_CAPACITY));
        assertThat(switches.isEnabled(SeededBug.OFF_BY_ONE_CAPACITY)).isTrue();
        Set<SeededBug> before = switches.snapshot();
        switches.replaceAll(List.of(SeededBug.RACE_ON_LAST_SLOT));
        assertThat(before).containsExactly(SeededBug.OFF_BY_ONE_CAPACITY);
        assertThat(switches.snapshot()).containsExactly(SeededBug.RACE_ON_LAST_SLOT);
        assertThatThrownBy(() -> switches.snapshot().add(SeededBug.OFF_BY_ONE_CAPACITY))
                .isInstanceOf(UnsupportedOperationException.class);
        switches.clear();
        assertThat(switches.snapshot()).isEmpty();
        assertThat(new MutableBugSwitches().snapshot()).isEmpty();
    }

    @Test
    void staticSwitchFactories() {
        assertThat(BugSwitches.none().isEnabled(SeededBug.RACE_ON_LAST_SLOT)).isFalse();
        assertThat(BugSwitches.of().isEnabled(SeededBug.RACE_ON_LAST_SLOT)).isFalse();
        BugSwitches two = BugSwitches.of(SeededBug.RACE_ON_LAST_SLOT, SeededBug.OFF_BY_ONE_CAPACITY);
        assertThat(two.isEnabled(SeededBug.OFF_BY_ONE_CAPACITY)).isTrue();
        assertThat(two.isEnabled(SeededBug.TIMEZONE_SLOT_TIMES)).isFalse();
    }

    @Test
    void everySeededBugIsDocumented() {
        for (SeededBug bug : SeededBug.values()) {
            assertThat(bug.description()).isNotBlank();
            assertThat(bug.location()).matches("(domain|api): \\w+");
        }
    }

    @Test
    void bookingReferencesMatchThePublishedPattern() {
        BookingReferences refs = new BookingReferences(new Random(42));
        Pattern p = Pattern.compile(BookingReferences.PATTERN);
        for (int i = 0; i < 500; i++) {
            assertThat(refs.next()).matches(p);
        }
        assertThat(new BookingReferences().next()).matches(p);
        assertThat(BookingReferences.ALPHABET).hasSize(32).doesNotContain("I", "L", "O", "U");
    }

    @Test
    void actorAndErrorCodes() {
        assertThat(new Actor(1, "c", Role.CLERK).isClerk()).isTrue();
        assertThat(new Actor(1, "d", Role.DRIVER).isClerk()).isFalse();
        assertThat(ErrorCode.SLOT_FULL.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(new DomainException(ErrorCode.FORBIDDEN, "x").code()).isEqualTo(ErrorCode.FORBIDDEN);
    }
}
