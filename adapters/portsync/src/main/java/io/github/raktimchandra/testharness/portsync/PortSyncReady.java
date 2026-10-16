package io.github.raktimchandra.testharness.portsync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raktimchandra.testharness.core.retry.Await;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits, once per JVM, until PortSync is worth testing: the core reports ready (database and
 * Kafka up), the simulator is running, and the core has processed the simulator's opening
 * inventory (so the yard is populated). Mirrors the readiness steps of PortSync's own
 * {@code scripts/smoke-test.sh}. A failure is remembered, so later tests fail fast with the same
 * reason instead of each waiting out the timeout.
 */
public final class PortSyncReady {

    private static final Logger LOG = LoggerFactory.getLogger(PortSyncReady.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static volatile String failure;
    private static volatile boolean ready;

    private PortSyncReady() {}

    public static synchronized void await(PortSync p) {
        if (ready) {
            return;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
        long start = System.nanoTime();
        try {
            Await wait = Await.atMost(p.readyTimeout()).pollEvery(Duration.ofSeconds(2));
            wait.until("PortSync core readiness at " + p.apiUrl() + "/actuator/health/readiness",
                    () -> status(p.apiUrl() + "/actuator/health/readiness") == 200);
            wait.until("the PortSync simulator to be running at " + p.simulatorUrl(),
                    () -> json(p.simulatorUrl() + "/api/simulation/status").map(j -> j.path("running").asBoolean())
                            .orElse(false));
            wait.until("the core to process at least " + p.minEventsProcessed() + " events",
                    () -> json(p.apiUrl() + "/api/overview").map(j -> j.path("eventsProcessed").asLong())
                            .orElse(0L) >= p.minEventsProcessed());
            ready = true;
            LOG.info("PortSync ready after {} s", Duration.ofNanos(System.nanoTime() - start).toSeconds());
        } catch (Await.AwaitTimeoutException e) {
            failure = "PortSync is not running or not ready: " + e.getMessage()
                    + ". Start it from a PortSync checkout with `docker compose up -d --build`"
                    + " (see adapters/portsync/README.md).";
            throw new IllegalStateException(failure, e);
        }
    }

    private static int status(String url) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static Optional<JsonNode> json(String url) {
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? Optional.of(JSON.readTree(r.body())) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
