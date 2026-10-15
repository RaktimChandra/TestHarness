package io.github.raktimchandra.testharness.ui.heal;

import java.util.ArrayList;
import java.util.List;

/**
 * A described UI element: a name for people, a primary locator, and ranked fallbacks.
 *
 * <pre>{@code
 * static final Target BOOK = Target.named("Book slot button")
 *         .testId("book-submit")
 *         .accessibleName("button", "Book slot")
 *         .text("button", "Book slot");
 * }</pre>
 * The first strategy is the contract; the others only exist so a drifted locator is diagnosed
 * with a precise fix instead of a bare "no such element".
 */
public final class Target {

    private final String name;
    private final List<Strategy> strategies;

    private Target(String name, List<Strategy> strategies) {
        this.name = name;
        this.strategies = List.copyOf(strategies);
    }

    public static Target named(String name) {
        return new Target(name, List.of());
    }

    public Target testId(String value) {
        return with(new Strategy.TestId(value));
    }

    public Target id(String value) {
        return with(new Strategy.Id(value));
    }

    public Target accessibleName(String css, String name) {
        return with(new Strategy.AccessibleName(css, name));
    }

    public Target text(String tag, String text) {
        return with(new Strategy.Text(tag, text));
    }

    public Target below(String tag, Strategy anchor) {
        return with(new Strategy.Below(tag, anchor));
    }

    public Target with(Strategy s) {
        List<Strategy> next = new ArrayList<>(strategies);
        next.add(s);
        return new Target(name, next);
    }

    public String name() {
        return name;
    }

    public List<Strategy> strategies() {
        return strategies;
    }

    public Strategy primary() {
        if (strategies.isEmpty()) {
            throw new IllegalStateException("Target '" + name + "' has no locator");
        }
        return strategies.get(0);
    }

    @Override
    public String toString() {
        return name + " [" + primary().describe() + "]";
    }
}
