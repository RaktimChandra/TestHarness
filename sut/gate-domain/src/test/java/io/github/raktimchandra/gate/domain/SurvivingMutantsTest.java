package io.github.raktimchandra.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests written because PIT mutants survived the first mutation run (2026-10-15: 7 survivors
 * of 143). Each test names the mutant it kills; see RESULTS.md, "Mutation testing".
 */
@DisplayName("Mutants that survived the first PIT run")
class SurvivingMutantsTest {

    @Test
    @DisplayName("ContainerNumber.problemWith: the message, not just 'invalid', is part of the contract (returned \"\")")
    void problemMessagesSayWhatIsWrong() {
        assertThat(ContainerNumber.problemWith("ABC", BugSwitches.none()))
                .isEqualTo("Container number must be 3 letters, a category letter (U, J or Z), 6 digits and a check digit");
        assertThat(ContainerNumber.problemWith("CSQU3054384", BugSwitches.none()))
                .isEqualTo("Check digit should be 3 but was 4");
        assertThat(ContainerNumber.problemWith("CSQU3054383", BugSwitches.none())).isNull();
    }

    @Test
    @DisplayName("BookingRules: each duration is checked for being negative on its own (removed conditionals)")
    void everyRuleDurationMustBeNonNegative() {
        Duration minus = Duration.ofMinutes(-1);
        Duration ok = Duration.ofMinutes(1);
        assertThatThrownBy(() -> new BookingRules(minus, ok, ok)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookingRules(ok, minus, ok)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookingRules(ok, ok, minus)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new BookingRules(Duration.ZERO, Duration.ZERO, Duration.ZERO))
                .as("zero is allowed: no cut-off, no early check-in, no grace").doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SlotWindow: zero and negative lengths are rejected, one second is fine (removed conditionals)")
    void slotLengthMustBePositive() {
        Instant t = Instant.parse("2026-11-20T03:30:00Z");
        assertThatThrownBy(() -> new SlotWindow(t, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("slot length must be positive");
        assertThatThrownBy(() -> new SlotWindow(t, Duration.ofMinutes(-30))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SlotWindow(t, Duration.ofSeconds(1)).end()).isEqualTo(t.plusSeconds(1));
    }

    @Test
    @DisplayName("MutableBugSwitches: a bug is off unless switched on (isEnabled returned true)")
    void bugsAreOffUnlessSwitchedOn() {
        MutableBugSwitches none = new MutableBugSwitches();
        for (SeededBug bug : SeededBug.values()) {
            assertThat(none.isEnabled(bug)).as(bug.name()).isFalse();
        }
        MutableBugSwitches one = new MutableBugSwitches(List.of(SeededBug.RACE_ON_LAST_SLOT));
        assertThat(one.isEnabled(SeededBug.RACE_ON_LAST_SLOT)).isTrue();
        assertThat(EnumSet.complementOf(EnumSet.of(SeededBug.RACE_ON_LAST_SLOT))).allSatisfy(
                b -> assertThat(one.isEnabled(b)).as(b.name()).isFalse());
    }
}
