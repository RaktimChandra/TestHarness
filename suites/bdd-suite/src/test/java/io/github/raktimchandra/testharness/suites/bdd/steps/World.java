package io.github.raktimchandra.testharness.suites.bdd.steps;

import io.github.raktimchandra.testharness.bdd.ScenarioContext;
import io.github.raktimchandra.testharness.gate.api.GateApi;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Scenario-scoped state shared by every step class (PicoContainer creates one per scenario
 * and injects it), built on the framework's typed {@link ScenarioContext}.
 */
public class World {

    static final ScenarioContext.Key<GateUser> ME = ScenarioContext.Key.of("me (the driver)", GateUser.class);
    static final ScenarioContext.Key<Models.Slot> SLOT = ScenarioContext.Key.of("the slot", Models.Slot.class);
    static final ScenarioContext.Key<Models.Booking> BOOKING = ScenarioContext.Key.of("the booking", Models.Booking.class);
    static final ScenarioContext.Key<Response> RESPONSE = ScenarioContext.Key.of("the last response", Response.class);
    static final ScenarioContext.Key<String> CONTAINER = ScenarioContext.Key.of("the container", String.class);

    final ScenarioContext ctx;
    final GateApi api = GateApi.get();
    final GateFixtures fx = GateFixtures.get();
    /** Bookings made with fixed (not generated) container numbers, cancelled after the scenario. */
    final List<Cleanup> cleanups = new ArrayList<>();

    record Cleanup(GateUser owner, String reference) {}

    public World(ScenarioContext ctx) {
        this.ctx = ctx;
    }
}
