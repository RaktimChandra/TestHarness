package io.github.raktimchandra.testharness.portsync.live;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.portsync.Models.Alert;
import io.github.raktimchandra.testharness.portsync.Models.BlockSummary;
import io.github.raktimchandra.testharness.portsync.Models.SimulationStatus;
import io.github.raktimchandra.testharness.portsync.PortSync;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * PortSync's SLA rules and live channels, driven through the simulator's fault injection:
 * each scenario makes one rule breach on purpose and checks that the core notices, through
 * its REST API and its Server-Sent Events stream. Isolated: surges and pauses change the
 * whole terminal.
 */
@Isolated
@DisplayName("PortSync: SLA rules, event stream and core/simulator agreement")
class SlaScenariosTest extends LiveTest {

    private Alert openAlert(String rule, String entity) {
        return await(alertTimeout()).until("an OPEN " + rule + " alert for " + entity, () -> core.alerts("OPEN"),
                        list -> list.stream().anyMatch(a -> rule.equals(a.rule()) && entity.equals(a.entityId())))
                .stream().filter(a -> rule.equals(a.rule()) && entity.equals(a.entityId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a gate surge into one block raises YARD_BLOCK_UTILISATION for that block, as the yard API shows")
    void gateSurgeRaisesYardUtilisation() {
        BlockSummary fullest = core.blocks().stream().max((a, b) -> Double.compare(a.utilisation(), b.utilisation()))
                .orElseThrow();
        int free = fullest.capacity() - fullest.used();
        // enough export trucks to take the block past the 85 % threshold (capped by the simulator's 200)
        int trucks = Math.max(1, Math.min(200, (int) Math.ceil(fullest.capacity() * 0.92) - fullest.used()));
        simulator.gateSurge(trucks, fullest.block());
        Alert alert = openAlert("YARD_BLOCK_UTILISATION", fullest.block());
        assertThat(alert.message()).contains(fullest.block());
        double utilisation = core.block(fullest.block()).utilisation();
        assertThat(utilisation).as("block %s utilisation after %d trucks (%d were free)", fullest.block(), trucks, free)
                .isGreaterThan(0.85);
    }

    @Test
    @DisplayName("a vessel surge leaves vessels waiting and raises VESSEL_BERTH_WAIT")
    void vesselSurgeRaisesBerthWait() {
        simulator.vesselSurge(6);
        Alert alert = await(alertTimeout().multipliedBy(2)).until("an OPEN VESSEL_BERTH_WAIT alert",
                        () -> core.alerts("OPEN"), list -> list.stream().anyMatch(a -> "VESSEL_BERTH_WAIT".equals(a.rule())))
                .stream().filter(a -> "VESSEL_BERTH_WAIT".equals(a.rule())).findFirst().orElseThrow();
        assertThat(alert.severity()).isEqualTo("WARNING");
        assertThat(core.overview().vesselsWaiting()).isPositive();
    }

    @Test
    @DisplayName("the SSE stream delivers processed events and a 'dlq' event for an invalid container")
    void eventStreamDeliversEventsAndDeadLetters() throws Exception {
        List<String> eventNames = new CopyOnWriteArrayList<>();
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(PortSync.get().apiUrl() + "/api/events/stream"))
                .header("Accept", "text/event-stream").build();
        CompletableFuture<Void> reader = http.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(res -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                        for (String line; (line = in.readLine()) != null; ) {
                            if (line.startsWith("event:")) {
                                eventNames.add(line.substring(6).strip());
                            }
                        }
                    } catch (java.io.IOException closed) {
                        // the test closes the stream when it is done
                    }
                });
        try {
            await(EVENT_TIMEOUT).until("a processed event on the stream", () -> eventNames.contains("event"), b -> b);
            simulator.invalidContainer();
            await(EVENT_TIMEOUT).until("a 'dlq' event on the stream", () -> eventNames.contains("dlq"), b -> b);
        } finally {
            reader.cancel(true);
            http.shutdownNow();
        }
        assertThat(eventNames).contains("event", "dlq");
    }

    @Test
    @DisplayName("with the simulation paused, the core's yard matches the simulator's, block by block")
    void coreAndSimulatorAgreeOnTheYard() {
        SimulationStatus paused = simulator.pause();
        try {
            assertThat(paused.paused()).isTrue();
            // wait until the core has consumed everything the simulator published
            List<Long> processed = new ArrayList<>();
            await(Duration.ofSeconds(60)).until("the core to stop processing events", () -> {
                processed.add(core.overview().eventsProcessed());
                int n = processed.size();
                return n >= 3 && processed.get(n - 1).equals(processed.get(n - 3));
            }, b -> b);
            Map<String, Integer> simulated = new TreeMap<>();
            simulator.status().terminal().yardOccupancy().forEach((b, n) -> {
                if (n > 0) {
                    simulated.put(b, n);
                }
            });
            Map<String, Integer> recorded = new TreeMap<>();
            core.blocks().stream().filter(b -> b.used() > 0).forEach(b -> recorded.put(b.block(), b.used()));
            assertThat(recorded).as("core yard (blocks API) vs simulator yard").isEqualTo(simulated);
        } finally {
            simulator.resume();
        }
    }
}
