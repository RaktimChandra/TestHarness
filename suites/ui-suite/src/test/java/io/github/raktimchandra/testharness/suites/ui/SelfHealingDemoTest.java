package io.github.raktimchandra.testharness.suites.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.github.raktimchandra.testharness.gate.ui.DriverPage;
import io.github.raktimchandra.testharness.ui.heal.Healing;
import io.github.raktimchandra.testharness.ui.heal.HealingEvent;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Demonstrates self-healing on the real app: the booking button's data-testid is renamed in
 * the live page (as a frontend refactor would), and the unchanged page object still books —
 * through its accessible-name fallback — while the test is marked healed with the exact fix.
 * This test is <em>meant</em> to heal; it is tagged so reports can tell it from a real drift.
 */
@Tag("healing-demo")
@DisplayName("Self-healing locator demo")
class SelfHealingDemoTest extends UiTest {

    @Test
    void a_renamed_test_id_is_healed_and_reported_with_the_fix(WebDriver driver) {
        GateUser user = fx.newDriver();
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(14, 0), 2);
        DriverPage page = pages().driverSignedInViaApi(user);
        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        page.enterContainer(GateFixtures.container());

        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector('[data-testid=\"book-submit\"]').setAttribute('data-testid', 'book-button');");

        String reference = page.submitAndGetReference();

        assertThat(reference).startsWith("GA-");
        assertThat(Healing.events()).hasSize(1);
        HealingEvent heal = Healing.events().get(0);
        assertThat(heal.target()).isEqualTo("Book slot button");
        assertThat(heal.brokenLocator()).isEqualTo("data-testid=\"book-submit\"");
        assertThat(heal.usedLocator()).isEqualTo("accessible name \"Book slot\" (button)");
        assertThat(heal.suggestedFix()).contains("data-testid is now \"book-button\"");
    }
}
