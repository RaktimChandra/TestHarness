package io.github.raktimchandra.testharness.perf;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
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
 * One level of the capacity experiment (tools/capacity.py): an open workload of booking flows
 * arriving at a fixed rate, with no pass/fail gates — the experiment reads the latency and
 * error figures of each level and finds where the system stops keeping up.
 *
 * <p>System properties: {@code perf.rate} (flows started per second), {@code perf.duration}
 * (seconds at that rate), {@code perf.flow}: {@code full} signs in on every flow (as the smoke
 * test does), {@code authenticated} signs each driver in once during set-up and reuses the
 * token, so the two flows differ only by the password check on the request path.
 */
public class CapacitySimulation extends Simulation {

    private static final double RATE = Double.parseDouble(System.getProperty("perf.rate", "10"));
    private static final int DURATION = Integer.getInteger("perf.duration", 60);
    private static final String FLOW = System.getProperty("perf.flow", "full");
    private static final int DRIVERS = 40;
    private static final int SLOTS = 20;

    private final GateApi api = GateApi.get();
    private final List<GateUser> drivers = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private final List<Long> slotIds = new ArrayList<>();
    private final String slotDate;

    {
        if (!FLOW.equals("full") && !FLOW.equals("authenticated")) {
            throw new IllegalArgumentException("perf.flow must be full or authenticated, not " + FLOW);
        }
        GateFixtures fx = GateFixtures.get();
        for (int i = 0; i < DRIVERS; i++) {
            GateUser d = fx.newDriver();
            drivers.add(d);
            if (FLOW.equals("authenticated")) {
                tokens.add(api.login(d.username(), d.credentials().password().reveal()).as(Models.Login.class).token());
            }
        }
        Models.Slot first = null;
        for (int i = 0; i < SLOTS; i++) {
            // 20 x 50 places (the API maximum per slot), far above what is booked at once: every flow cancels.
            Models.Slot s = fx.slotTomorrowAt(LocalTime.of(6, 0).plusMinutes(30L * (i % 10)), 50);
            slotIds.add(s.id());
            first = first == null ? s : first;
        }
        slotDate = first.start().toLocalDate().toString();
    }

    private final AtomicInteger next = new AtomicInteger();

    private final Iterator<Map<String, Object>> feeder = Stream.generate(() -> {
        int i = Math.floorMod(next.getAndIncrement(), DRIVERS);
        GateUser d = drivers.get(i);
        return Map.<String, Object>of(
                "username", d.username(),
                "password", d.credentials().password().reveal(),
                "token", tokens.isEmpty() ? "" : tokens.get(i),
                "slotId", slotIds.get(ThreadLocalRandom.current().nextInt(SLOTS)),
                "container", ContainerNumbers.random(TestData.random()),
                "date", slotDate);
    }).iterator();

    private final HttpProtocolBuilder protocol = http.baseUrl(api.baseUrl())
            .acceptHeader("application/json").contentTypeHeader("application/json")
            .shareConnections();

    private final ChainBuilder signIn = exec(http("login").post("/api/auth/login")
            .body(StringBody("{\"username\":\"#{username}\",\"password\":\"#{password}\"}"))
            .check(status().is(200), jsonPath("$.token").saveAs("token")));

    private final ChainBuilder work = exec(http("list slots").get("/api/slots?date=#{date}")
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

    private final ScenarioBuilder flow = FLOW.equals("full")
            ? scenario("Booking flow, signing in each time").feed(feeder).exec(signIn).exec(work)
            : scenario("Booking flow, already signed in").feed(feeder).exec(work);

    {
        setUp(flow.injectOpen(constantUsersPerSec(RATE).during(Duration.ofSeconds(DURATION)).randomized()))
                .protocols(protocol)
                .maxDuration(Duration.ofSeconds(DURATION + 120L));
    }
}
