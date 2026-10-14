package io.github.raktimchandra.gate.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.raktimchandra.gate.domain.MutableBugSwitches;
import io.github.raktimchandra.gate.domain.SeededBug;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The SUT's own developer tests: Spring context, Flyway schema, security and the one
 * property that unit tests cannot show — that the slot row lock really serialises
 * concurrent bookings (and that the seeded race defect really breaks it).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "gate.test-support.enabled=true", "gate.test-support.key=it-key", "gate.race-window=150ms",
    // Pinned so the test does not depend on a GATE_SEED_PASSWORD in the environment (CI sets one).
    "gate.seed.password=" + GateApiIntegrationTest.PASSWORD})
class GateApiIntegrationTest {

    static final String PASSWORD = "integration-test-pass";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestRestTemplate http;

    @Autowired
    MutableBugSwitches bugs;

    @AfterEach
    void resetBugs() {
        bugs.clear();
    }

    @Test
    void seededUsersCanLogInAndBadPasswordsAreRejectedWithAProblem() {
        assertThat(login("driver1").get("user").get("role").asText()).isEqualTo("DRIVER");
        ResponseEntity<JsonNode> bad = http.postForEntity("/api/auth/login",
                Map.of("username", "driver1", "password", "wrong"), JsonNode.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bad.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(bad.getBody().get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void apiRequiresATokenButHealthAndUiDoNot() {
        assertThat(http.getForEntity("/api/slots?date=2026-01-01", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void driverBooksAndClerkSeesIt() {
        String clerk = token("clerk1");
        long slotId = createSlot(clerk, 2);
        String driver = token(newUser("DRIVER"));
        ResponseEntity<JsonNode> booked = book(driver, slotId, "CSQU3054383");
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String ref = booked.getBody().get("reference").asText();
        assertThat(ref).matches("GA-[0-9A-HJKMNP-TV-Z]{8}");
        ResponseEntity<JsonNode> seen = exchange(HttpMethod.GET, "/api/bookings/" + ref, clerk, null);
        assertThat(seen.getBody().get("status").asText()).isEqualTo("BOOKED");
        assertThat(seen.getBody().get("slot").get("start").asText()).endsWith("+05:30");
    }

    @Test
    void rowLockSerialisesConcurrentBookingsForTheLastPlace() throws Exception {
        assertThat(raceForLastPlace(8)).isEqualTo(1);
    }

    @Test
    void seededRaceDefectLetsConcurrentBookingsOverfillTheSlot() throws Exception {
        bugs.replaceAll(List.of(SeededBug.RACE_ON_LAST_SLOT));
        assertThat(raceForLastPlace(8)).isGreaterThan(1);
    }

    @Test
    void testSupportNeedsTheKey() {
        assertThat(http.getForEntity("/api/test-support/bugs", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        HttpHeaders h = new HttpHeaders();
        h.set("X-Test-Support-Key", "it-key");
        assertThat(http.exchange("/api/test-support/bugs", HttpMethod.GET, new HttpEntity<>(h), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Fires {@code drivers} simultaneous bookings at a capacity-1 slot; returns how many succeeded. */
    private int raceForLastPlace(int drivers) throws Exception {
        long slotId = createSlot(token("clerk1"), 1);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < drivers; i++) {
            tokens.add(token(newUser("DRIVER")));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(drivers);
        try {
            List<Future<HttpStatus>> results = new ArrayList<>();
            for (int i = 0; i < drivers; i++) {
                String t = tokens.get(i);
                String container = containerFor(i);
                Callable<HttpStatus> task = () -> {
                    start.await();
                    return HttpStatus.valueOf(book(t, slotId, container).getStatusCode().value());
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int created = 0;
            for (Future<HttpStatus> f : results) {
                HttpStatus s = f.get();
                assertThat(s).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT);
                created += s == HttpStatus.CREATED ? 1 : 0;
            }
            return created;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String containerFor(int i) {
        String prefix = String.format("TSTU%06d", 100000 + i * 7 + (int) (Math.random() * 1000) * 100);
        return prefix + checkDigit(prefix);
    }

    private static int checkDigit(String p) {
        int[] letters = new int[26];
        int next = 10;
        for (int i = 0; i < 26; i++) {
            if (next % 11 == 0) {
                next++;
            }
            letters[i] = next++;
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = p.charAt(i);
            sum += (Character.isDigit(c) ? c - '0' : letters[c - 'A']) << i;
        }
        return sum % 11 % 10;
    }

    private long createSlot(String clerkToken, int capacity) {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).truncatedTo(ChronoUnit.MINUTES);
        String lane = "T" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ResponseEntity<JsonNode> r = exchange(HttpMethod.POST, "/api/slots", clerkToken,
                Map.of("lane", lane, "start", start.toString(), "capacity", capacity));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id").asLong();
    }

    private ResponseEntity<JsonNode> book(String token, long slotId, String container) {
        return exchange(HttpMethod.POST, "/api/bookings", token,
                Map.of("slotId", slotId, "containerNumber", container, "direction", "DROP_OFF"));
    }

    private String newUser(String role) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Test-Support-Key", "it-key");
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/test-support/users", HttpMethod.POST, new HttpEntity<>(Map.of("role", role), h),
                JsonNode.class).getBody().get("username").asText();
    }

    private JsonNode login(String username) {
        ResponseEntity<JsonNode> r = http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", PASSWORD), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private String token(String username) {
        return login(username).get("token").asText();
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }
}
