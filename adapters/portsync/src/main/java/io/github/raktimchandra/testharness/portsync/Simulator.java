package io.github.raktimchandra.testharness.portsync;

import static io.restassured.RestAssured.given;

import io.github.raktimchandra.testharness.api.ApiSpecs;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.portsync.Models.Injection;
import io.github.raktimchandra.testharness.portsync.Models.SimulationStatus;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * PortSync's simulator (port 8081): its fault-injection API is the test-support interface.
 * Every injection returns the events it published, so a test can follow exactly those event ids
 * through Kafka into the core instead of guessing.
 */
public final class Simulator {

    private final RequestSpecification spec;

    public Simulator(PortSync portSync, HarnessConfig config) {
        this.spec = ApiSpecs.base(portSync.simulatorUrl(), PortSync.schemas(), config);
    }

    public static Simulator get() {
        return new Simulator(PortSync.get(), HarnessConfig.get());
    }

    private Injection inject(Response r) {
        if (r.statusCode() != 200) {
            throw new AssertionError("Fault injection failed with HTTP " + r.statusCode() + ": " + r.asString());
        }
        return r.as(Injection.class);
    }

    public Injection invalidContainer() {
        return inject(given().spec(spec).post("/api/faults/invalid-container"));
    }

    public Injection outOfOrder() {
        return inject(given().spec(spec).post("/api/faults/out-of-order"));
    }

    public Injection duplicates(int count) {
        return inject(given().spec(spec).queryParam("count", count).post("/api/faults/duplicates"));
    }

    /** kind: GATE_OUT_NOT_IN_YARD, MOVE_ON_FAULTED_CRANE, OUTSIDE_YARD or MALFORMED_JSON. */
    public Injection impossible(String kind) {
        return inject(given().spec(spec).queryParam("kind", kind).post("/api/faults/impossible"));
    }

    public Injection faultCrane(String craneId, String code) {
        return inject(given().spec(spec).queryParam("code", code).post("/api/faults/cranes/" + craneId + "/fault"));
    }

    public Injection recoverCrane(String craneId) {
        return inject(given().spec(spec).post("/api/faults/cranes/" + craneId + "/recover"));
    }

    public SimulationStatus status() {
        Response r = given().spec(spec).get("/api/simulation/status");
        if (r.statusCode() != 200) {
            throw new AssertionError("Simulator status returned HTTP " + r.statusCode() + ": " + r.asString());
        }
        return r.as(SimulationStatus.class);
    }

    public Response raw(String method, String path) {
        return given().spec(spec).request(method, path);
    }
}
