package io.github.raktimchandra.testharness.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.ui.heal.Healer;
import io.github.raktimchandra.testharness.ui.heal.Healing;
import io.github.raktimchandra.testharness.ui.heal.HealingEvent;
import io.github.raktimchandra.testharness.ui.heal.HealingMode;
import io.github.raktimchandra.testharness.ui.heal.Target;
import io.github.raktimchandra.testharness.ui.junit.BrowserExtension;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Self-healing against a real browser and a page whose test ids have "drifted"
 * (save → save-v2, user → user-v2), plus the cases that must never heal.
 */
@Tag("browser")
@ExtendWith(BrowserExtension.class)
class HealingTest {

    static final Target SAVE = Target.named("Save button").testId("save")
            .accessibleName("button", "Save").text("button", "Save");
    static final Target USER = Target.named("Username field").testId("user").accessibleName("input", "Username");

    @BeforeEach
    void open(WebDriver driver) throws Exception {
        driver.get(HealingTest.class.getResource("/healing.html").toURI().toString());
    }

    private static WebElement locate(WebDriver d, Healer h, Target t) {
        return h.accept(t, h.attempt(d, t).orElseThrow(() -> h.notFound(d, t)));
    }

    @Test
    void aDriftedTestIdIsHealedReportedAndGivenAPreciseFix(WebDriver driver, Waits waits) {
        Healer healer = new Healer(driver, HealingMode.REPORT);
        locate(driver, healer, SAVE).click();
        waits.text(org.openqa.selenium.By.cssSelector("[data-testid=out]"), "saved");
        locate(driver, healer, USER).sendKeys("driver1");

        assertThat(Healing.events()).hasSize(2);
        HealingEvent save = Healing.events().get(0);
        assertThat(save.target()).isEqualTo("Save button");
        assertThat(save.brokenLocator()).isEqualTo("data-testid=\"save\"");
        assertThat(save.usedLocator()).isEqualTo("accessible name \"Save\" (button)");
        assertThat(save.rank()).isEqualTo(1);
        assertThat(save.suggestedFix()).contains("data-testid is now \"save-v2\"").contains("testId(\"save-v2\")");
        assertThat(save.test()).isEqualTo("HealingTest.aDriftedTestIdIsHealedReportedAndGivenAPreciseFix");
        assertThat(Healing.events().get(1).usedLocator()).isEqualTo("accessible name \"Username\" (input)");
    }

    @Test
    void failModeDiagnosesButDoesNotContinue(WebDriver driver) {
        Healer healer = new Healer(driver, HealingMode.FAIL);
        assertThatThrownBy(() -> locate(driver, healer, SAVE)).isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Locator broken for 'Save button'")
                .hasMessageContaining("data-testid is now \"save-v2\"");
        assertThat(Healing.events()).isEmpty();
    }

    @Test
    void offModeUsesThePrimaryLocatorOnly(WebDriver driver) {
        Healer healer = new Healer(driver, HealingMode.OFF);
        assertThatThrownBy(() -> locate(driver, healer, SAVE)).isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Tried: data-testid=\"save\" → 0 matches");
    }

    @Test
    void anAmbiguousPrimaryIsATestBugAndIsNeverHealed(WebDriver driver) {
        Healer healer = new Healer(driver, HealingMode.REPORT);
        Target twin = Target.named("Twin").testId("twin").text("button", "Twin");
        assertThatThrownBy(() -> healer.attempt(driver, twin)).isInstanceOf(Healer.AmbiguousLocatorException.class)
                .hasMessageContaining("matches 2 elements");
    }

    @Test
    void ambiguousFallbacksAreSkippedAndTheFailureListsEveryAttempt(WebDriver driver) {
        Healer healer = new Healer(driver, HealingMode.REPORT);
        Target delete = Target.named("Delete button").testId("delete").text("button", "Delete");
        assertThatThrownBy(() -> locate(driver, healer, delete)).isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("data-testid=\"delete\" → 0 matches")
                .hasMessageContaining("button with text \"Delete\" → 2 matches")
                .hasMessageContaining("ambiguous fallbacks are never used");
        assertThat(Healing.events()).isEmpty();
    }

    @Test
    void aWorkingPrimaryIsNotAHeal(WebDriver driver) {
        Healer healer = new Healer(driver, HealingMode.REPORT);
        locate(driver, healer, Target.named("Form").testId("form"));
        assertThat(Healing.events()).isEmpty();
    }
}
