package io.github.raktimchandra.testharness.ui.page;

import org.openqa.selenium.By;

/** Locator conventions. {@code data-testid} is the contract between the UI and its tests. */
public final class Locators {

    private Locators() {}

    public static By testId(String id) {
        return By.cssSelector("[data-testid=\"" + cssEscape(id) + "\"]");
    }

    /** Elements whose test id starts with {@code prefix}, e.g. every {@code booking-row-*}. */
    public static By testIdPrefix(String prefix) {
        return By.cssSelector("[data-testid^=\"" + cssEscape(prefix) + "\"]");
    }

    static String cssEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
