package io.github.raktimchandra.testharness.gate.ui;

import static io.github.raktimchandra.testharness.ui.page.Locators.testId;

import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.page.BasePage;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;

/** The driver's page: book a slot, see bookings, reschedule and cancel. */
public class DriverPage extends BasePage<DriverPage> {

    static final By FORM = testId("book-form");
    static final By CONTAINER = testId("book-container");
    static final By CONTAINER_HINT = testId("book-container-error");
    static final By DIRECTION = testId("book-direction");
    static final By SUBMIT = testId("book-submit");
    static final By SELECTED = testId("book-selected-slot");
    static final By NOTICE = testId("driver-notice");
    static final By ERROR = testId("book-error");
    static final By PAGE_ERROR = testId("driver-error");
    static final By CONFIRM_YES = testId("confirm-yes");
    static final By CONFIRM_NO = testId("confirm-no");
    static final By DIALOG = testId("confirm-dialog");
    static final By RESCHEDULE_CONFIRM = testId("reschedule-confirm");
    static final By RESCHEDULE_ERROR = testId("reschedule-error");
    private static final Pattern REFERENCE = Pattern.compile("GA-[0-9A-HJKMNP-TV-Z]{8}");

    DriverPage(WebDriver driver, Waits waits, String baseUrl) {
        super(driver, waits, baseUrl);
    }

    @Override
    protected By readyLocator() {
        return FORM;
    }

    @Override
    protected String path() {
        return "/driver";
    }

    public SlotPicker slots() {
        return new SlotPicker(driver, waits, "slot");
    }

    public SlotPicker rescheduleSlots() {
        return new SlotPicker(driver, waits, "reschedule");
    }

    public DriverPage enterContainer(String container) {
        type(CONTAINER, container);
        find(CONTAINER).sendKeys(org.openqa.selenium.Keys.TAB); // blur triggers validation feedback
        return this;
    }

    public String containerHint() {
        return text(CONTAINER_HINT);
    }

    public boolean containerMarkedInvalid() {
        return "true".equals(find(CONTAINER).getDomAttribute("aria-invalid"));
    }

    public DriverPage direction(String value) {
        new Select(find(DIRECTION)).selectByValue(value);
        return this;
    }

    public boolean submitEnabled() {
        return find(SUBMIT).isEnabled();
    }

    public String selectedSlotText() {
        return text(SELECTED);
    }

    /** Submits the form and returns the booking reference shown in the confirmation. */
    public String submitAndGetReference() {
        return Report.step("Submit booking", () -> {
            click(SUBMIT);
            String notice = waits.textContains(NOTICE, "Booked ").getText();
            Matcher m = REFERENCE.matcher(notice);
            if (!m.find()) {
                throw new AssertionError("No booking reference in confirmation: " + notice);
            }
            return m.group();
        });
    }

    public void submit() {
        click(SUBMIT);
    }

    public String notice() {
        return text(NOTICE);
    }

    public String bookingError() {
        return text(ERROR);
    }

    public String pageError() {
        return text(PAGE_ERROR);
    }

    // ---- bookings table ----

    public String status(String reference) {
        return find(testId("booking-status-" + reference)).getDomAttribute("data-status");
    }

    public DriverPage waitForStatus(String reference, String status) {
        waits.attribute(testId("booking-status-" + reference), "data-status", status);
        return this;
    }

    public String slotText(String reference) {
        return text(testId("booking-slot-" + reference));
    }

    public boolean hasActions(String reference) {
        return !driver.findElements(testId("booking-cancel-" + reference)).isEmpty();
    }

    public DriverPage startReschedule(String reference) {
        click(testId("booking-reschedule-" + reference));
        waits.visible(testId("reschedule-panel"));
        return this;
    }

    public DriverPage confirmReschedule() {
        Report.step("Confirm reschedule", () -> click(RESCHEDULE_CONFIRM));
        return this;
    }

    public String rescheduleError() {
        return text(RESCHEDULE_ERROR);
    }

    public DriverPage cancel(String reference, boolean confirm) {
        Report.step((confirm ? "Cancel " : "Start cancelling, then keep ") + reference, () -> {
            click(testId("booking-cancel-" + reference));
            waits.visible(DIALOG);
            click(confirm ? CONFIRM_YES : CONFIRM_NO);
            waits.gone(DIALOG);
        });
        return this;
    }

    public void logout() {
        click(testId("logout-button"));
    }
}
