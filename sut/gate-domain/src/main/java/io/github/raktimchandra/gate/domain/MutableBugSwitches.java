package io.github.raktimchandra.gate.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, runtime-switchable bug registry. Reads are lock-free; each update swaps in a
 * new immutable set, so a request never sees a half-applied change.
 */
public final class MutableBugSwitches implements BugSwitches {

    private final AtomicReference<Set<SeededBug>> active =
            new AtomicReference<>(Collections.unmodifiableSet(EnumSet.noneOf(SeededBug.class)));

    public MutableBugSwitches() {}

    public MutableBugSwitches(Collection<SeededBug> initial) {
        replaceAll(initial);
    }

    @Override
    public boolean isEnabled(SeededBug bug) {
        return active.get().contains(bug);
    }

    /** Replaces the whole active set atomically. */
    public void replaceAll(Collection<SeededBug> bugs) {
        EnumSet<SeededBug> copy = EnumSet.noneOf(SeededBug.class);
        copy.addAll(bugs);
        active.set(Collections.unmodifiableSet(copy));
    }

    public void clear() {
        replaceAll(EnumSet.noneOf(SeededBug.class));
    }

    /** An immutable snapshot of the currently active bugs. */
    public Set<SeededBug> snapshot() {
        return active.get();
    }
}
