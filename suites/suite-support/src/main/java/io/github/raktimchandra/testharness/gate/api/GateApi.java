package io.github.raktimchandra.testharness.gate.api;

import static io.restassured.RestAssured.given;

import io.github.raktimchandra.testharness.api.ApiSpecs;
import io.github.raktimchandra.testharness.api.auth.Credentials;
import io.github.raktimchandra.testharness.api.auth.TokenCache;
import io.github.raktimchandra.testharness.api.schema.SchemaRegistry;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.gate.sut.GateSut;
import io.github.raktimchandra.testharness.reporting.Report;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Gate API as an object. Two levels:
 * <ul>
 *   <li>{@code raw*} / request methods return the {@link Response} so tests can assert
 *       negative, boundary and authorisation cases exactly;</li>
 *   <li>typed helpers ({@link #book}, {@link #createSlot}, ...) assert success and return a
 *       model, for setting up preconditions in one line.</li>
 * </ul>
 * Every response passes through schema validation (see harness-api).
 */
public final class GateApi {

    public static final String TEST_SUPPORT_HEADER = "X-Test-Support-Key";

    private static volatile GateApi instance;

    private final GateSut sut;
    private final SchemaRegistry schemas = SchemaRegistry.load("schemas");
    private final TokenCache tokens;

    private GateApi(GateSut sut) {
        this.sut = sut;
        this.tokens = new TokenCache(this::loginToken);
    }

    public static GateApi get() {
        GateApi a = instance;
        if (a == null) {
            synchronized (GateApi.class) {
                a = instance;
                if (a == null) {
                    a = new GateApi(GateSut.get());
                    instance = a;
                }
            }
        }
        return a;
    }

    public String baseUrl() {
        return sut.baseUrl();
    }

    /** A request with no credentials. */
    public RequestSpecification anonymous() {
        return given().spec(ApiSpecs.base(sut.baseUrl(), schemas, HarnessConfig.get()));
    }

    /** A request authenticated as {@code user} (token cached per user). */
    public RequestSpecification as(GateUser user) {
        return anonymous().auth().oauth2(tokens.tokenFor(user.credentials()));
    }

    /** A request carrying the test-support key. */
    public RequestSpecification testSupport() {
        return anonymous().header(TEST_SUPPORT_HEADER, sut.testSupportKey());
    }

    // ---- auth -------------------------------------------------------------------------

    public Response login(String username, String password) {
        return anonymous().body(Map.of("username", username, "password", password)).post("/api/auth/login");
    }

    private String loginToken(Credentials c) {
        Response r = login(c.username(), c.password().reveal());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Login as " + c.username() + " failed: " + r.statusCode() + " " + r.asString());
        }
        return r.as(Models.Login.class).token();
    }

    // ---- slots -----------------------------------------------------------------------

    public Response rawCreateSlot(GateUser user, Map<String, Object> body) {
        return as(user).body(body).post("/api/slots");
    }

    public Models.Slot createSlot(GateUser clerk, String lane, OffsetDateTime start, int capacity) {
        return Report.step("Clerk opens slot " + lane + " at " + start + " (capacity " + capacity + ")", () -> {
            Response r = rawCreateSlot(clerk, Map.of("lane", lane, "start", start.toString(), "capacity", capacity));
            expect(r, 201);
            return r.as(Models.Slot.class);
        });
    }

    public Response rawSlots(GateUser user, LocalDate date) {
        return as(user).queryParam("date", date.toString()).get("/api/slots");
    }

    public Models.Slot slot(GateUser user, long id) {
        Response r = as(user).get("/api/slots/" + id);
        expect(r, 200);
        return r.as(Models.Slot.class);
    }

    // ---- bookings --------------------------------------------------------------------

    public Response rawBook(GateUser driver, long slotId, String container, String direction) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("slotId", slotId);
        body.put("containerNumber", container);
        body.put("direction", direction);
        return as(driver).body(body).post("/api/bookings");
    }

    public Models.Booking book(GateUser driver, long slotId, String container) {
        return Report.step(driver.username() + " books slot " + slotId + " for " + container, () -> {
            Response r = rawBook(driver, slotId, container, "DROP_OFF");
            expect(r, 201);
            return r.as(Models.Booking.class);
        });
    }

    public Response rawGetBooking(GateUser user, String reference) {
        return as(user).get("/api/bookings/" + reference);
    }

    public Models.Booking booking(GateUser user, String reference) {
        Response r = rawGetBooking(user, reference);
        expect(r, 200);
        return r.as(Models.Booking.class);
    }

    public Response rawSearch(GateUser user, Map<String, ?> filters) {
        return as(user).queryParams(filters).get("/api/bookings");
    }

    public List<Models.Booking> search(GateUser user, Map<String, ?> filters) {
        Response r = rawSearch(user, filters);
        expect(r, 200);
        return List.of(r.as(Models.Booking[].class));
    }

    public Response rawReschedule(GateUser user, String reference, long slotId) {
        return as(user).body(Map.of("slotId", slotId)).put("/api/bookings/" + reference + "/slot");
    }

    public Response rawCancel(GateUser user, String reference) {
        return as(user).post("/api/bookings/" + reference + "/cancel");
    }

    public Response rawCheckIn(GateUser user, String reference) {
        return as(user).post("/api/bookings/" + reference + "/check-in");
    }

    private static void expect(Response r, int status) {
        if (r.statusCode() != status) {
            throw new AssertionError("Expected HTTP " + status + " but got " + r.statusCode() + ": " + r.asString());
        }
    }
}
