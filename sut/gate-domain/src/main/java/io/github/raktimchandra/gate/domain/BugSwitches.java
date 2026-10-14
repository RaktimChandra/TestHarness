package io.github.raktimchandra.gate.domain;

import java.util.EnumSet;
import java.util.Set;

/** Read-side view of which seeded bugs are active. */
@FunctionalInterface
public interface BugSwitches {

    boolean isEnabled(SeededBug bug);

    /** No bugs active: the correct system. */
    static BugSwitches none() {
        return bug -> false;
    }

    /** A fixed set of active bugs (convenient in unit tests). */
    static BugSwitches of(SeededBug... bugs) {
        Set<SeededBug> active = bugs.length == 0 ? EnumSet.noneOf(SeededBug.class) : EnumSet.of(bugs[0], bugs);
        return active::contains;
    }
}
