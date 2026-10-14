package io.github.raktimchandra.testharness.perf;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.github.raktimchandra.testharness.data.TestData;
import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import io.github.raktimchandra.testharness.gate.api.GateApi;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Performance smoke test of the driver booking flow:
 * login -> list slots -> book -> read booking -> cancel.
 *
 * <p>Load and gates are system properties so CI and a laptop can use different budgets:
 * {@code perf.users} (virtual users, default 60), {@code perf.ramp} (seconds, default 30),
 * {@code perf.p95} / {@code perf.p99} (ms, defaults 800 / 1500), {@code perf.maxErrorPct}
 * (default 1.0). The gates fail the build when exceeded.
 */
public class BookingFlowSimulation extends Simulation {

    private static final int USERS = Integer.getInteger("perf.users", 60);
    private static final int RAMP_SECONDS = Integer.getInteger("perf.ramp", 30);
    private static final int P95_MS = Integer.getInteger("perf.p95", 800);
    private static final int P99_MS = Integer.getInteger("perf.p99", 1500);
    private static final double MAX_ERROR_PCT = Double.parseDouble(System.getProperty("perf.maxErrorPct", "1.0"));
    private static final int DRIVERS = 30;
    private static final int SLOTS = 10;

    private final GateApi api = GateApi.get();
    private final List<GateUser> drivers = new ArrayList<>();
    private final List<Long> slotIds = new ArrayList<>();
    private final String slotDate;

    {
        // Test data, created through the API before any load is applied.
        GateFixtures fx = GateFixtures.get();
        for (int i = 0; i < DRIVERS; i++) {
            drivers.add(fx.newDriver());
        }
        Models.Slot first = null;
        for (int i = 0; i < SLOTS; i++) {
            Models.Slot s = fx.slotTomorrowAt(LocalTime.of(6, 0).plusMinutes(30L * i), 50);
            slotIds.add(s.id());
            first = first == null ? s : first;
        }
        slotDate = first.start().toLocalDate().toString();
    }

    private final AtomicInteger next = new AtomicInteger();

    private final Iterator<Map<String, Object>> feeder = Stream.generate(() -> {
        GateUser d = drivers.get(Math.floorMod(next.getAndIncrement(), DRIVERS));
        return Map.<String, Object>of(
                "username", d.username(),
                "password", d.credentials().password().reveal(),
                "slotId", slotIds.get(ThreadLocalRandom.current().nextInt(SLOTS)),
                "container", ContainerNumbers.random(TestData.random()),
                "date", slotDate);
    }).iterator();

    private final HttpProtocolBuilder protocol = http.baseUrl(api.baseUrl())
            .acceptHeader("application/json").contentTypeHeader("application/json");

    private final ScenarioBuilder bookingFlow = scenario("Driver booking flow")
            .feed(feeder)
            .exec(http("login").post("/api/auth/login")
                    .body(StringBody("{\"username\":\"#{username}\",\"password\":\"#{password}\"}"))
                    .check(status().is(200), jsonPath("$.token").saveAs("token")))
            .exec(http("list slots").get("/api/slots?date=#{date}")
                    .header("Authorization", "Bearer #{token}")
                    .check(status().is(200)))
            .exec(http("book").post("/api/bookings")
                    .header("Authorization", "Bearer #{token}")
                    .body(StringBody("{\"slotId\":#{slotId},\"containerNumber\":\"#{container}\",\"direction\":\"DROP_OFF\"}"))
                    .check(status().is(201), jsonPath("$.reference").saveAs("reference")))
            .exec(http("read booking").get("/api/bookings/#{reference}")
                    .header("Authorization", "Bearer #{token}")
                    .check(status().is(200)))
            .exec(http("cancel").post("/api/bookings/#{reference}/cancel")
                    .header("Authorization", "Bearer #{token}")
                    .check(status().is(200)));

    {
        setUp(bookingFlow.injectOpen(rampUsers(USERS).during(Duration.ofSeconds(RAMP_SECONDS))))
                .protocols(protocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(P95_MS),
                        global().responseTime().percentile(99.0).lt(P99_MS),
                        global().failedRequests().percent().lt(MAX_ERROR_PCT));
    }
}
