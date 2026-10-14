package io.github.raktimchandra.testharness.suites.bdd.steps;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.LocalTime;

/** Booking-rule steps. They act through the API layer (harness-api + typed Gate client). */
public class BookingSteps {

    private final World w;

    public BookingSteps(World world) {
        this.w = world;
    }

    @Given("a gate slot tomorrow at {time} with room for {int} truck(s)")
    public void slotTomorrow(LocalTime time, int capacity) {
        w.ctx.put(World.SLOT, w.fx.slotTomorrowAt(time, capacity));
    }

    @Given("a gate slot starting in {int} minutes with room for {int} truck(s)")
    public void slotSoon(int minutes, int capacity) {
        w.ctx.put(World.SLOT, w.fx.slotStartingIn(Duration.ofMinutes(minutes), capacity));
    }

    @Given("I am a driver")
    public void iAmADriver() {
        w.ctx.put(World.ME, w.fx.newDriver());
    }

    @Given("{int} other drivers have already booked that slot")
    public void othersBooked(int count) {
        for (int i = 0; i < count; i++) {
            w.api.book(w.fx.newDriver(), slot().id(), GateFixtures.container());
        }
    }

    @Given("a driver has booked that slot")
    public void aDriverHasBooked() {
        GateUser driver = w.fx.newDriver();
        w.ctx.put(World.ME, driver);
        w.ctx.put(World.BOOKING, w.api.book(driver, slot().id(), GateFixtures.container()));
    }

    @Given("I have booked that slot for a valid container")
    public void iHaveBooked() {
        String container = GateFixtures.container();
        w.ctx.put(World.CONTAINER, container);
        w.ctx.put(World.BOOKING, w.api.book(me(), slot().id(), container));
    }

    @When("I book that slot for a valid container")
    public void iBookValid() {
        iBook(GateFixtures.container(), false);
    }

    @When("I book that slot for container {string}")
    public void iBookContainer(String container) {
        iBook(container, true);
    }

    private void iBook(String container, boolean fixedNumber) {
        Response r = w.api.rawBook(me(), slot().id(), container, "DROP_OFF");
        w.ctx.put(World.RESPONSE, r);
        if (r.statusCode() == 201) {
            Models.Booking b = r.as(Models.Booking.class);
            w.ctx.put(World.BOOKING, b);
            if (fixedNumber) {
                w.cleanups.add(new World.Cleanup(me(), b.reference()));
            }
        }
    }

    @When("another driver books a different slot for the same container")
    public void anotherDriverSameContainer() {
        Models.Slot other = w.fx.slotTomorrowAt(LocalTime.of(11, 0), 1);
        w.ctx.put(World.RESPONSE, w.api.rawBook(w.fx.newDriver(), other.id(), w.ctx.get(World.CONTAINER), "DROP_OFF"));
    }

    @When("another driver looks up my booking")
    public void anotherDriverLooksUp() {
        w.ctx.put(World.RESPONSE, w.api.rawGetBooking(w.fx.newDriver(), booking().reference()));
    }

    @When("I cancel my booking")
    public void iCancel() {
        w.ctx.put(World.RESPONSE, w.api.rawCancel(me(), booking().reference()));
    }

    @When("a clerk checks the truck in")
    public void clerkChecksIn() {
        w.ctx.put(World.RESPONSE, w.api.rawCheckIn(w.fx.newClerk(), booking().reference()));
    }

    @When("I try to check myself in")
    public void driverChecksIn() {
        w.ctx.put(World.RESPONSE, w.api.rawCheckIn(me(), booking().reference()));
    }

    @Then("the booking is confirmed with a reference")
    public void confirmed() {
        assertThatResponse(w.ctx.get(World.RESPONSE)).hasStatus(201);
        assertThat(booking().reference()).matches("GA-[0-9A-HJKMNP-TV-Z]{8}");
    }

    @Then("the outcome is confirmed")
    public void outcomeConfirmed() {
        assertThatResponse(w.ctx.get(World.RESPONSE)).hasStatus(201);
    }

    @Then("the outcome is refused because {string}")
    @Then("the booking is refused because {string}")
    @Then("their booking is refused because {string}")
    @Then("the change is refused because {string}")
    @Then("the check-in is refused because {string}")
    public void refused(String code) {
        Response r = w.ctx.get(World.RESPONSE);
        assertThat(r.statusCode()).as("HTTP status").isBetween(400, 499);
        assertThatResponse(r).hasProblemCode(code);
    }

    @Then("they are told it does not exist")
    public void doesNotExist() {
        assertThatResponse(w.ctx.get(World.RESPONSE)).hasStatus(404).hasProblemCode("NOT_FOUND");
    }

    @Then("the slot has {int} place(s) left")
    public void placesLeft(int remaining) {
        assertThat(w.api.slot(me(), slot().id()).remaining()).isEqualTo(remaining);
    }

    @Then("my booking is {string}")
    @Then("the booking is {string}")
    public void bookingStatus(String status) {
        assertThatResponse(w.ctx.get(World.RESPONSE)).hasStatus(200);
        assertThat(w.api.booking(GateFixtures.seeded("clerk1"), booking().reference()).status()).isEqualTo(status);
    }

    @After
    public void releaseFixedContainers() {
        for (World.Cleanup c : w.cleanups) {
            w.api.rawCancel(c.owner(), c.reference());
        }
    }

    private GateUser me() {
        return w.ctx.get(World.ME);
    }

    private Models.Slot slot() {
        return w.ctx.get(World.SLOT);
    }

    private Models.Booking booking() {
        return w.ctx.get(World.BOOKING);
    }
}
