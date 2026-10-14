package io.github.raktimchandra.testharness.ui.page;

import java.time.LocalDate;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Input helpers for controls that keyboard automation handles inconsistently.
 *
 * <p>Native {@code <input type="date">} expects keystrokes in the browser's locale order
 * (mm/dd/yyyy in one browser, dd/mm/yyyy in another), so typing is unreliable across
 * Chrome, Firefox and Edge. Instead the value is set through the element's native value
 * setter and an {@code input} event is dispatched, which is exactly what frameworks such as
 * React listen for. The value the page ends up with is then asserted, so this cannot
 * silently fail.
 */
public final class Inputs {

    private Inputs() {}

    public static void setDate(WebDriver driver, WebElement input, LocalDate date) {
        ((JavascriptExecutor) driver).executeScript("""
                const el = arguments[0];
                const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                setter.call(el, arguments[1]);
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                """, input, date.toString());
        String actual = input.getDomProperty("value");
        if (!date.toString().equals(actual)) {
            throw new IllegalStateException("Date input did not accept " + date + " (value is '" + actual + "')");
        }
    }
}
