package io.github.raktimchandra.testharness.portsync.ui;

import io.github.raktimchandra.testharness.ui.heal.Target;
import io.github.raktimchandra.testharness.ui.page.BasePage;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Container lookup: type an ISO 6346 number, see the live check-digit verdict, look it up.
 * PortSync has no data-testid attributes, so the controls are {@link Target}s led by their id and
 * accessible name; a renamed id heals through the label and is reported, never silently.
 */
public final class ContainerLookupPage extends BasePage<ContainerLookupPage> {

    static final Target NUMBER = Target.named("Container number field")
            .id("container-number").accessibleName("input", "Container number");
    static final Target LOOK_UP = Target.named("Look up button")
            .accessibleName("button", "Look up").text("button", "Look up");
    private static final By VERDICT = By.id("container-check");
    private static final By RESULT = By.id("result-heading");

    ContainerLookupPage(WebDriver driver, Waits waits, String baseUrl) {
        super(driver, waits, baseUrl);
    }

    @Override
    protected By readyLocator() {
        return By.cssSelector("form[role='search'] input");
    }

    @Override
    protected String path() {
        return "/containers";
    }

    public ContainerLookupPage enter(String number) {
        type(NUMBER, number);
        return this;
    }

    /** The live verdict under the field: "Valid ISO 6346 number" or the reason it is not. */
    public String verdict() {
        return text(VERDICT);
    }

    /**
     * True if the verdict reads exactly {@code expected} within a short wait (React re-renders
     * after each keystroke); false otherwise, so a caller can collect every disagreement.
     */
    public boolean verdictBecomes(String expected) {
        return waits.withTimeout(java.time.Duration.ofSeconds(3)).holdsWithin("verdict '" + expected + "'",
                d -> expected.equals(d.findElement(VERDICT).getText().strip()));
    }

    public boolean lookUpEnabled() {
        return find(LOOK_UP).isEnabled();
    }

    public ContainerLookupPage lookUp() {
        click(LOOK_UP);
        return this;
    }

    public String resultNumber() {
        return text(RESULT);
    }

    /** A fact from the result panel, e.g. "Status" → "In yard". */
    public String fact(String term) {
        return text(By.xpath("//dl[contains(@class,'facts')]//div[dt[normalize-space(.)='" + term + "']]/dd"));
    }

    public List<String> history() {
        waits.visible(RESULT);
        return driver.findElements(By.cssSelector("section[aria-labelledby='result-heading'] ol.timeline li"))
                .stream().map(WebElement::getText).map(String::strip).toList();
    }

    public String notice() {
        return text(By.cssSelector("p.notice"));
    }
}
