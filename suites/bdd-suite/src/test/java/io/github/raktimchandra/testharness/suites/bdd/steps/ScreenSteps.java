package io.github.raktimchandra.testharness.suites.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.github.raktimchandra.testharness.gate.ui.ClerkPage;
import io.github.raktimchandra.testharness.gate.ui.DriverPage;
import io.github.raktimchandra.testharness.gate.ui.GatePages;
import io.github.raktimchandra.testharness.bdd.ScenarioContext;

/** UI steps for @ui scenarios; the browser is started and stopped by the framework hooks. */
public class ScreenSteps {

    private static final ScenarioContext.Key<ClerkPage> CLERK_PAGE = ScenarioContext.Key.of("clerk page", ClerkPage.class);
    private static final ScenarioContext.Key<DriverPage> DRIVER_PAGE = ScenarioContext.Key.of("driver page", DriverPage.class);
    private static final ScenarioContext.Key<String> REFERENCE = ScenarioContext.Key.of("reference", String.class);

    private final World w;

    public ScreenSteps(World world) {
        this.w = world;
    }

    @When("a clerk finds the booking on the gate screen and checks it in")
    public void clerkChecksInOnScreen() {
        Models.Booking b = w.ctx.get(World.BOOKING);
        Models.Slot slot = w.ctx.get(World.SLOT);
        ClerkPage page = GatePages.current().clerkSignedInViaApi(w.fx.newClerk())
                .searchByReference(b.reference(), slot.start().toLocalDate())
                .checkIn(b.reference());
        w.ctx.put(CLERK_PAGE, page);
    }

    @Then("the gate screen shows the booking as checked in")
    public void screenShowsCheckedIn() {
        Models.Booking b = w.ctx.get(World.BOOKING);
        ClerkPage page = w.ctx.get(CLERK_PAGE);
        assertThat(page.status(b.reference())).isEqualTo("CHECKED_IN");
        assertThat(page.canCheckIn(b.reference())).isFalse();
    }

    @When("a driver books that slot on the booking screen")
    public void driverBooksOnScreen() {
        Models.Slot slot = w.ctx.get(World.SLOT);
        DriverPage page = GatePages.current().driverSignedInViaApi(w.fx.newDriver());
        page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
        w.ctx.put(REFERENCE, page.enterContainer(GateFixtures.container()).submitAndGetReference());
        w.ctx.put(DRIVER_PAGE, page);
    }

    @Then("the booking screen lists it as booked at {string}")
    public void screenListsBooked(String timeRange) {
        DriverPage page = w.ctx.get(DRIVER_PAGE);
        String ref = w.ctx.get(REFERENCE);
        page.waitForStatus(ref, "BOOKED");
        assertThat(page.slotText(ref)).contains(timeRange);
    }
}
