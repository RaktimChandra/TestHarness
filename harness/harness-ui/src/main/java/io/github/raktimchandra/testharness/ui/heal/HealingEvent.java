package io.github.raktimchandra.testharness.ui.heal;

/**
 * A locator that failed and the fallback that found the element instead.
 *
 * @param suggestedFix what to change so the primary locator works again
 */
public record HealingEvent(String test, String target, String brokenLocator, String usedLocator, int rank,
                           String suggestedFix, String url) {}
