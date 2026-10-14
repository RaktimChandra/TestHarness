package io.github.raktimchandra.testharness.gate.ui;

import static io.github.raktimchandra.testharness.ui.page.Locators.testId;

import io.github.raktimchandra.testharness.ui.page.Inputs;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.time.LocalDate;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * The slot table used in two places (booking form, reschedule panel); the {@code prefix}
 * selects which instance, which is why one component class serves both.
 */
public class SlotPicker {

    private final WebDriver driver;
    private final Waits waits;
    private final String prefix;

    SlotPicker(WebDriver driver, Waits waits, String prefix) {
        this.driver = driver;
        this.waits = waits;
        this.prefix = prefix;
    }

    public SlotPicker showDate(LocalDate date) {
        WebElement input = waits.visible(testId(prefix + "-date"));
        Inputs.setDate(driver, input, date);
        waits.visible(testId(prefix + "-table"));
        return this;
    }

    public SlotPicker select(long slotId) {
        waits.clickable(testId(prefix + "-select-" + slotId)).click();
        waits.attribute(testId(prefix + "-select-" + slotId), "aria-pressed", "true");
        return this;
    }

    public String timeRange(long slotId) {
        return waits.visible(testId(prefix + "-time-" + slotId)).getText().strip();
    }

    public int remaining(long slotId) {
        return Integer.parseInt(waits.visible(testId(prefix + "-remaining-" + slotId)).getText().strip());
    }

    public boolean selectable(long slotId) {
        return waits.visible(testId(prefix + "-select-" + slotId)).isEnabled();
    }

    public boolean listed(long slotId) {
        waits.visible(testId(prefix + "-table"));
        return !driver.findElements(testId(prefix + "-row-" + slotId)).isEmpty();
    }
}
