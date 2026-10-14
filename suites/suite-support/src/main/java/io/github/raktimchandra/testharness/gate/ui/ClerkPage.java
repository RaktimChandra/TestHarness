package io.github.raktimchandra.testharness.gate.ui;

import static io.github.raktimchandra.testharness.ui.page.Locators.testId;

import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.page.BasePage;
import io.github.raktimchandra.testharness.ui.page.Inputs;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.time.LocalDate;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;

/** The gate clerk's page: find appointments and check trucks in. */
public class ClerkPage extends BasePage<ClerkPage> {

    static final By FORM = testId("search-form");
    static final By REFERENCE = testId("search-reference");
    static final By CONTAINER = testId("search-container");
    static final By STATUS = testId("search-status");
    static final By DATE = testId("search-date");
    static final By SUBMIT = testId("search-submit");
    static final By COUNT = testId("results-count");
    static final By NOTICE = testId("clerk-notice");
    static final By ERROR = testId("clerk-error");

    ClerkPage(WebDriver driver, Waits waits, String baseUrl) {
        super(driver, waits, baseUrl);
    }

    @Override
    protected By readyLocator() {
        return FORM;
    }

    @Override
    protected String path() {
        return "/clerk";
    }

    public ClerkPage searchByReference(String reference, LocalDate slotDate) {
        return Report.step("Search for " + reference, () -> {
            type(REFERENCE, reference);
            Inputs.setDate(driver, find(DATE), slotDate);
            click(SUBMIT);
            waits.visible(testId("result-row-" + reference));
            return this;
        });
    }

    public ClerkPage searchByContainer(String container, LocalDate slotDate, String status) {
        type(CONTAINER, container);
        Inputs.setDate(driver, find(DATE), slotDate);
        new Select(find(STATUS)).selectByValue(status == null ? "" : status);
        click(SUBMIT);
        waits.visible(COUNT);
        return this;
    }

    public int resultCount() {
        return Integer.parseInt(text(COUNT).replaceAll("[^0-9]", ""));
    }

    public String status(String reference) {
        return find(testId("result-status-" + reference)).getDomAttribute("data-status");
    }

    public boolean canCheckIn(String reference) {
        return !driver.findElements(testId("checkin-" + reference)).isEmpty();
    }

    public ClerkPage checkIn(String reference) {
        Report.step("Check in " + reference, () -> {
            click(testId("checkin-" + reference));
            waits.attribute(testId("result-status-" + reference), "data-status", "CHECKED_IN");
        });
        return this;
    }

    public ClerkPage clickCheckIn(String reference) {
        click(testId("checkin-" + reference));
        return this;
    }

    public String notice() {
        return text(NOTICE);
    }

    public String error() {
        return text(ERROR);
    }
}
