package io.github.raktimchandra.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BookingAccessPolicyTest {

    private static final Actor ALICE = new Actor(1, "alice", Role.DRIVER);
    private static final Actor BOB = new Actor(2, "bob", Role.DRIVER);
    private static final Actor CLERK = new Actor(9, "clerk", Role.CLERK);

    private final BookingAccessPolicy correct = new BookingAccessPolicy(BugSwitches.none());
    private final BookingAccessPolicy buggy =
            new BookingAccessPolicy(BugSwitches.of(SeededBug.MISSING_AUTHORISATION_CHECK));

    @Test
    void ownerCanViewAndModify() {
        assertThat(correct.canView(ALICE, 1)).isTrue();
        assertThat(correct.canModify(ALICE, 1)).isTrue();
    }

    @Test
    void otherDriverCannotViewOrModify() {
        assertThat(correct.canView(BOB, 1)).isFalse();
        assertThat(correct.canModify(BOB, 1)).isFalse();
        assertThatThrownBy(() -> correct.requireView(BOB, 1)).hasMessage("Booking not found");
        assertThatThrownBy(() -> correct.requireModify(BOB, 1))
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void clerkCanViewButNotModifyAndOnlyClerkChecksIn() {
        assertThat(correct.canView(CLERK, 1)).isTrue();
        assertThat(correct.canModify(CLERK, 1)).isFalse();
        assertThatThrownBy(() -> correct.requireModify(CLERK, 1))
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(correct.canCheckIn(CLERK)).isTrue();
        assertThat(correct.canCheckIn(ALICE)).isFalse();
        assertThatCode(() -> correct.requireCheckIn(CLERK)).doesNotThrowAnyException();
        assertThatThrownBy(() -> correct.requireCheckIn(ALICE))
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void requireViewPassesForOwnerAndClerk() {
        assertThatCode(() -> correct.requireView(ALICE, 1)).doesNotThrowAnyException();
        assertThatCode(() -> correct.requireView(CLERK, 1)).doesNotThrowAnyException();
        assertThatCode(() -> correct.requireModify(ALICE, 1)).doesNotThrowAnyException();
    }

    @Test
    void seededBugOpensIdor() {
        assertThat(buggy.canView(BOB, 1)).isTrue();
        assertThat(buggy.canModify(BOB, 1)).isTrue();
        // The seeded defect does not extend check-in rights to drivers.
        assertThat(buggy.canCheckIn(BOB)).isFalse();
        assertThat(buggy.canModify(CLERK, 1)).isFalse();
    }
}
