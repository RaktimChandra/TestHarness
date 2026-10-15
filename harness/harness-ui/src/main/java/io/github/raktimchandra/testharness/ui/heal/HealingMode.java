package io.github.raktimchandra.testharness.ui.heal;

import java.util.Locale;

/**
 * {@code ui.healing}:
 * <ul>
 *   <li>{@code report} (default) — use a fallback, continue, and mark the test <b>healed</b> in the
 *       report with the suggested fix</li>
 *   <li>{@code fail} — still locate the element to diagnose it, then fail with the suggested fix</li>
 *   <li>{@code off} — primary locator only, as if no fallbacks existed</li>
 * </ul>
 */
public enum HealingMode {
    REPORT, FAIL, OFF;

    public static HealingMode parse(String s) {
        return valueOf(s.strip().toUpperCase(Locale.ROOT));
    }
}
