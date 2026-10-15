package io.github.raktimchandra.testharness.ui.heal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Finds a {@link Target}. Rules, so healing never hides a real problem:
 * <ul>
 *   <li>The primary locator must match <b>exactly one</b> element. Two matches is a test bug
 *       and fails immediately; it is never "healed".</li>
 *   <li>A fallback is used only if it matches exactly one element; ambiguous fallbacks are
 *       skipped.</li>
 *   <li>Every heal is recorded with the locator that broke, the one that worked, and a
 *       suggested fix read from the element actually found (e.g. its current data-testid).</li>
 * </ul>
 */
public final class Healer {

    /** An element and the rank (0 = primary) of the strategy that found it. */
    public record Located(WebElement element, int rank, Strategy strategy) {}

    private final WebDriver driver;
    private final HealingMode mode;

    public Healer(WebDriver driver, HealingMode mode) {
        this.driver = driver;
        this.mode = mode;
    }

    /** One attempt, no side effects; used inside explicit waits. Empty if nothing matched yet. */
    public Optional<Located> attempt(SearchContext context, Target target) {
        List<Strategy> strategies = target.strategies();
        List<WebElement> primary = strategies.get(0).find(context);
        if (primary.size() > 1) {
            throw new AmbiguousLocatorException(target, strategies.get(0), primary.size());
        }
        if (primary.size() == 1) {
            return Optional.of(new Located(primary.get(0), 0, strategies.get(0)));
        }
        if (mode == HealingMode.OFF) {
            return Optional.empty();
        }
        for (int i = 1; i < strategies.size(); i++) {
            List<WebElement> found = strategies.get(i).find(context);
            if (found.size() == 1) {
                return Optional.of(new Located(found.get(0), i, strategies.get(i)));
            }
        }
        return Optional.empty();
    }

    /** Accepts a located element: records a heal (or fails, in {@code fail} mode) when rank &gt; 0. */
    public WebElement accept(Target target, Located located) {
        if (located.rank() == 0) {
            return located.element();
        }
        HealingEvent event = new HealingEvent(Healing.currentTest(), target.name(), target.primary().describe(),
                located.strategy().describe(), located.rank(), suggestFix(target, located.element()),
                safeUrl());
        if (mode == HealingMode.FAIL) {
            throw new NoSuchElementException("Locator broken for '" + target.name() + "': " + event.brokenLocator()
                    + " matched nothing, but " + event.usedLocator() + " found it. Suggested fix: "
                    + event.suggestedFix() + " (ui.healing=fail)");
        }
        Healing.record(event);
        return located.element();
    }

    /** A clear failure listing every strategy tried and how many elements each matched. */
    public NoSuchElementException notFound(SearchContext context, Target target) {
        List<String> tried = new ArrayList<>();
        for (Strategy s : target.strategies()) {
            int n = s.find(context).size();
            tried.add(s.describe() + " → " + n + (n == 1 ? " match" : " matches"));
            if (mode == HealingMode.OFF) {
                break;
            }
        }
        return new NoSuchElementException("'" + target.name() + "' not found. Tried: " + String.join("; ", tried)
                + (tried.stream().anyMatch(t -> t.endsWith("matches") && !t.endsWith(" 0 matches"))
                        ? " (ambiguous fallbacks are never used)" : ""));
    }

    static String suggestFix(Target target, WebElement found) {
        String testId = found.getDomAttribute("data-testid");
        String id = found.getDomAttribute("id");
        Strategy primary = target.primary();
        if (primary instanceof Strategy.TestId && testId != null && !testId.isBlank()) {
            return "the element's data-testid is now \"" + testId + "\": update the Target to testId(\"" + testId
                    + "\"), or restore data-testid in the UI if the rename was accidental";
        }
        if (primary instanceof Strategy.TestId) {
            return "the element has no data-testid any more: restore data-testid=\""
                    + ((Strategy.TestId) primary).value() + "\" in the UI"
                    + (id != null && !id.isBlank() ? " (it has id=\"" + id + "\")" : "");
        }
        if (primary instanceof Strategy.Id && id != null && !id.isBlank()) {
            return "the element's id is now \"" + id + "\": update the Target";
        }
        return "the primary locator " + primary.describe() + " no longer matches; the element was found by "
                + "its fallback — give it a data-testid and make that the primary locator";
    }

    private String safeUrl() {
        try {
            return driver.getCurrentUrl();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The primary locator matched several elements: always a test bug, never healed. */
    public static class AmbiguousLocatorException extends RuntimeException {
        public AmbiguousLocatorException(Target target, Strategy s, int n) {
            super("'" + target.name() + "': primary locator " + s.describe() + " matches " + n
                    + " elements; a locator must identify exactly one");
        }
    }
}
