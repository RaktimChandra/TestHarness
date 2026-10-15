package io.github.raktimchandra.testharness.suites.flaky;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.logging.LoggingContextExtension;
import io.github.raktimchandra.testharness.gate.api.GateApi;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Two drivers race for the last place on a slot. The product is correct: exactly one wins.
 *
 * <p>{@link #the_driver_who_asks_first_gets_the_last_place()} is the <b>deliberately flaky</b>
 * test. It encodes a very common wrong assumption — "the request I submitted first is served
 * first" — when both requests are released at the same instant on different threads. Its
 * outcome depends on thread scheduling, connection reuse and the database lock queue, so it
 * passes and fails on the same commit. That makes it the demonstration case for flakiness
 * scoring and quarantine; the stable control asserts what the product actually guarantees.
 */
@Tag("flaky-demo")
@ExtendWith(LoggingContextExtension.class)
@DisplayName("Last place race (flaky demo)")
class LastPlaceRaceTest {

    private final GateApi api = GateApi.get();
    private final GateFixtures fx = GateFixtures.get();

    private record Race(int first, int second) {}

    private Race race() throws Exception {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(16, 0), 1);
        GateUser a = fx.newDriver();
        GateUser b = fx.newDriver();
        api.as(a).get("/api/auth/me");
        api.as(b).get("/api/auth/me");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> {
                go.await();
                return api.rawBook(a, slot.id(), GateFixtures.container(), "DROP_OFF").statusCode();
            });
            Future<Integer> second = pool.submit(() -> {
                go.await();
                return api.rawBook(b, slot.id(), GateFixtures.container(), "DROP_OFF").statusCode();
            });
            go.countDown();
            return new Race(first.get(), second.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void the_driver_who_asks_first_gets_the_last_place() throws Exception {
        Race r = race();
        assertThat(r.first()).as("first-submitted request (WRONG assumption: submission order = service order)")
                .isEqualTo(201);
    }

    @Test
    void exactly_one_driver_gets_the_last_place() throws Exception {
        Race r = race();
        assertThat(List.of(r.first(), r.second())).containsExactlyInAnyOrder(201, 409);
    }
}
