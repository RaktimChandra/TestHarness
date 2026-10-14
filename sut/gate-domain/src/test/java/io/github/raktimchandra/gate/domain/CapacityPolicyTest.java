package io.github.raktimchandra.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CapacityPolicyTest {

    private final CapacityPolicy correct = new CapacityPolicy(BugSwitches.none());
    private final CapacityPolicy buggy = new CapacityPolicy(BugSwitches.of(SeededBug.OFF_BY_ONE_CAPACITY));

    @ParameterizedTest(name = "{0}/{1} taken -> room: {2}")
    @CsvSource({"0,1,true", "0,4,true", "3,4,true", "4,4,false", "5,4,false", "0,0,false"})
    void boundaryValues(int taken, int capacity, boolean room) {
        assertThat(correct.hasRoom(taken, capacity)).isEqualTo(room);
    }

    @Test
    void seededBugAllowsOneExtra() {
        assertThat(buggy.hasRoom(4, 4)).isTrue();
        assertThat(buggy.hasRoom(5, 4)).isFalse();
    }

    @Test
    void requireRoomThrowsSlotFull() {
        assertThatThrownBy(() -> correct.requireRoom(4, 4))
                .isInstanceOf(DomainException.class)
                .hasMessage("Slot is full (4/4 places taken)")
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.SLOT_FULL);
    }

    @Test
    void requireRoomPassesWhenThereIsRoom() {
        correct.requireRoom(3, 4);
    }

    @Test
    void remainingNeverNegative() {
        assertThat(correct.remaining(1, 4)).isEqualTo(3);
        assertThat(correct.remaining(4, 4)).isZero();
        assertThat(correct.remaining(5, 4)).isZero();
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> correct.hasRoom(-1, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> correct.hasRoom(1, -4)).isInstanceOf(IllegalArgumentException.class);
    }
}
