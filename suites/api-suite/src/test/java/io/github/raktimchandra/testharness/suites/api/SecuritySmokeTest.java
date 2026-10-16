package io.github.raktimchandra.testharness.suites.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Security smoke checks: the attacks a reviewer would try first. Not a penetration test —
 * a fast, repeatable guard against the most common regressions (see TEST_STRATEGY.md).
 */
@Tag("security")
@DisplayName("Security smoke")
class SecuritySmokeTest extends ApiTest {

    private static JWTClaimsSet claims(String subject, long uid, String role, Instant expires) {
        return new JWTClaimsSet.Builder().issuer("gate-appointment").subject(subject).claim("uid", uid)
                .claim("role", role).issueTime(new Date()).expirationTime(Date.from(expires)).build();
    }

    // ---- authentication bypass ------------------------------------------------------

    @Test
    void an_unsigned_alg_none_token_is_rejected() {
        String token = new PlainJWT(new PlainHeader.Builder().type(JOSEObjectType.JWT).build(),
                claims("clerk1", 4, "CLERK", Instant.now().plusSeconds(3600))).serialize();
        assertThatResponse(api.anonymous().auth().oauth2(token).get("/api/bookings")).hasStatus(401)
                .hasProblemCode("UNAUTHENTICATED");
    }

    @Test
    void a_token_signed_with_an_attackers_key_is_rejected() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                claims("clerk1", 4, "CLERK", Instant.now().plusSeconds(3600)));
        jwt.sign(new MACSigner(key));
        assertThatResponse(api.anonymous().auth().oauth2(jwt.serialize()).get("/api/bookings")).hasStatus(401);
    }

    @Test
    void a_token_without_bearer_scheme_or_with_an_empty_value_is_rejected() {
        assertThatResponse(api.anonymous().header("Authorization", "Basic ZHJpdmVyMTpnYXRl").get("/api/bookings"))
                .hasStatus(401);
        assertThatResponse(api.anonymous().header("Authorization", "Bearer ").get("/api/bookings")).hasStatus(401);
    }

    // ---- authorisation (IDOR and role) --------------------------------------------

    @Test
    void a_driver_cannot_read_change_or_cancel_another_drivers_booking() {
        GateUser owner = fx.newDriver();
        GateUser attacker = fx.newDriver();
        Models.Booking b = api.book(owner, fx.slotTomorrowAt(LocalTime.of(7, 0), 2).id(), GateFixtures.container());
        Models.Slot other = fx.slotTomorrowAt(LocalTime.of(7, 30), 2);
        assertThatResponse(api.rawGetBooking(attacker, b.reference())).hasStatus(404);
        assertThatResponse(api.rawReschedule(attacker, b.reference(), other.id())).hasStatus(404);
        assertThatResponse(api.rawCancel(attacker, b.reference())).hasStatus(404);
        Models.Booking after = api.booking(owner, b.reference());
        assertThat(after.status()).isEqualTo("BOOKED");
        assertThat(after.slot().id()).isEqualTo(b.slot().id());
    }

    @Test
    void a_driver_cannot_use_clerk_only_operations() {
        GateUser driver = fx.newDriver();
        Models.Booking b = api.book(driver, fx.slotTomorrowAt(LocalTime.of(8, 0), 1).id(), GateFixtures.container());
        assertThatResponse(api.rawCheckIn(driver, b.reference())).hasStatus(403).hasProblemCode("FORBIDDEN");
        assertThatResponse(api.rawCreateSlot(driver, Map.of("lane", "X", "start", "2030-01-01T10:00:00Z", "capacity", 1)))
                .hasStatus(403);
    }

    @Test
    void mass_assignment_of_status_or_owner_is_ignored() {
        GateUser driver = fx.newDriver();
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(8, 30), 1);
        Map<String, Object> body = new HashMap<>();
        body.put("slotId", slot.id());
        body.put("containerNumber", GateFixtures.container());
        body.put("direction", "DROP_OFF");
        body.put("status", "CHECKED_IN");
        body.put("reference", "GA-HACKED00");
        body.put("driver", Map.of("username", "clerk1"));
        Response r = api.as(driver).body(body).post("/api/bookings");
        assertThatResponse(r).hasStatus(201);
        Models.Booking b = r.as(Models.Booking.class);
        assertThat(b.status()).isEqualTo("BOOKED");
        assertThat(b.reference()).isNotEqualTo("GA-HACKED00");
        assertThat(b.driver().username()).isEqualTo(driver.username());
    }

    // ---- injection ---------------------------------------------------------------

    @ParameterizedTest(name = "reference {0}")
    @ValueSource(strings = {"GA-' OR '1'='1", "GA-1;DROP TABLE booking", "../../actuator/env", "%27%20OR%201=1"})
    void hostile_references_are_just_not_found(String reference) {
        Response r = api.as(fx.newDriver()).urlEncodingEnabled(true).get("/api/bookings/{ref}", reference);
        assertThat(r.statusCode()).isIn(400, 404);
        assertThat(r.jsonPath().getString("code")).isIn("NOT_FOUND", "VALIDATION_FAILED");
    }

    @ParameterizedTest(name = "username {0}")
    @ValueSource(strings = {"' OR 1=1 --", "admin'--", "\" OR \"\"=\""})
    void sql_injection_in_login_fails_closed(String username) {
        // A unique suffix per run: each failure counts towards that username's lock.
        assertThatResponse(api.login(username + System.nanoTime(), "x' OR '1'='1")).hasStatus(401)
                .hasProblemCode("UNAUTHENTICATED");
    }

    // ---- brute force ----------------------------------------------------------------

    @Test
    void five_failed_sign_ins_lock_the_account_even_against_the_right_password() {
        GateUser user = fx.newDriver();
        for (int i = 1; i <= 5; i++) {
            assertThatResponse(api.login(user.username(), "guess-" + i)).hasStatus(401).hasProblemCode("UNAUTHENTICATED");
        }
        Response locked = api.login(user.username(), user.credentials().password().reveal());
        assertThatResponse(locked).hasStatus(429).hasProblemCode("TOO_MANY_ATTEMPTS");
        assertThat(Integer.parseInt(locked.header("Retry-After"))).isBetween(1, 300);
        assertThat(locked.jsonPath().getString("detail")).doesNotContain(user.username());
        // The lock is per account: another account still signs in.
        GateUser other = fx.newDriver();
        assertThatResponse(api.login(other.username(), other.credentials().password().reveal())).hasStatus(200);
    }

    @Test
    void a_lock_looks_the_same_for_an_account_that_does_not_exist() {
        String ghost = "ghost-" + System.nanoTime();
        for (int i = 1; i <= 5; i++) {
            assertThatResponse(api.login(ghost, "guess-" + i)).hasStatus(401);
        }
        GateUser real = fx.newDriver();
        for (int i = 1; i <= 5; i++) {
            assertThatResponse(api.login(real.username(), "guess-" + i)).hasStatus(401);
        }
        Response forGhost = api.login(ghost, "guess-6");
        Response forReal = api.login(real.username(), "guess-6");
        assertThatResponse(forGhost).hasStatus(429).hasProblemCode("TOO_MANY_ATTEMPTS");
        assertThatResponse(forReal).hasStatus(429).hasProblemCode("TOO_MANY_ATTEMPTS");
        assertThat(forGhost.jsonPath().getString("detail")).isEqualTo(forReal.jsonPath().getString("detail"));
    }

    @Test
    void script_payloads_are_rejected_and_never_reflected() {
        String payload = "<script>alert(1)</script>";
        Response r = api.rawBook(fx.newDriver(), fx.slotTomorrowAt(LocalTime.of(9, 0), 1).id(), payload, "DROP_OFF");
        assertThatResponse(r).hasStatus(400);
        assertThat(r.asString()).doesNotContain("<script>");
        Response lane = api.rawCreateSlot(clerk(), Map.of("lane", payload, "start", "2030-01-01T10:00:00Z", "capacity", 1));
        assertThatResponse(lane).hasStatus(400);
        assertThat(lane.asString()).doesNotContain("<script>");
    }

    // ---- transport and error hygiene -----------------------------------------------

    @Test
    void responses_carry_security_headers() {
        Response api1 = api.as(fx.newDriver()).get("/api/auth/me");
        assertThat(api1.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(api1.header("X-Frame-Options")).isEqualTo("DENY");
        assertThat(api1.header("Cache-Control")).contains("no-store");
        Response page = api.anonymous().accept("text/html").get("/login");
        assertThat(page.header("Content-Security-Policy")).contains("default-src 'self'").contains("frame-ancestors 'none'");
    }

    @Test
    void errors_do_not_leak_internals() {
        Response r = api.as(fx.newDriver()).body("{\"slotId\": \"not-a-number\"}").post("/api/bookings");
        assertThatResponse(r).hasStatus(400);
        String body = r.asString();
        assertThat(body).doesNotContain("Exception", "at io.", "org.springframework", "SQL", "jdbc");
    }

    @Test
    void test_support_is_not_reachable_without_its_key() {
        assertThatResponse(api.anonymous().body(Map.of("role", "CLERK")).post("/api/test-support/users"))
                .hasStatus(403);
    }
}
