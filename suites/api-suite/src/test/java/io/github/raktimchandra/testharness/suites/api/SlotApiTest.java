package io.github.raktimchandra.testharness.suites.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Slots API")
class SlotApiTest extends ApiTest {

    private static Map<String, Object> slotBody(String lane, OffsetDateTime start, Object capacity) {
        Map<String, Object> m = new HashMap<>();
        m.put("lane", lane);
        m.put("start", start.toString());
        m.put("capacity", capacity);
        return m;
    }

    private static OffsetDateTime tomorrowAt(int hour) {
        return ZonedDateTime.of(LocalDate.now(GateFixtures.TERMINAL).plusDays(1), LocalTime.of(hour, 0),
                GateFixtures.TERMINAL).toOffsetDateTime();
    }

    @Tag("smoke")
    @Test
    void clerk_opens_a_slot_and_gets_201_with_a_location() {
        String lane = GateFixtures.uniqueLane();
        Response r = api.rawCreateSlot(clerk(), slotBody(lane, tomorrowAt(10), 3));
        assertThatResponse(r).hasStatus(201);
        Models.Slot slot = r.as(Models.Slot.class);
        assertThat(r.header("Location")).isEqualTo("/api/slots/" + slot.id());
        assertThat(slot.lane()).isEqualTo(lane);
        assertThat(slot.capacity()).isEqualTo(3);
        assertThat(slot.remaining()).isEqualTo(3);
        assertThat(Duration.between(slot.start(), slot.end())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void slot_times_are_the_terminal_wall_clock_with_its_offset() {
        OffsetDateTime start = tomorrowAt(9);
        Models.Slot slot = api.createSlot(clerk(), GateFixtures.uniqueLane(), start, 1);
        assertThat(slot.start().toInstant()).as("same instant as requested").isEqualTo(start.toInstant());
        assertThat(slot.start().toLocalTime()).as("terminal wall-clock time").isEqualTo(LocalTime.of(9, 0));
        assertThat(slot.start().getOffset()).isEqualTo(start.getOffset());
    }

    @Test
    void a_slot_requested_in_utc_is_reported_in_terminal_time() {
        OffsetDateTime startUtc = tomorrowAt(14).withOffsetSameInstant(java.time.ZoneOffset.UTC);
        Models.Slot slot = api.createSlot(clerk(), GateFixtures.uniqueLane(), startUtc, 1);
        assertThat(slot.start().toInstant()).isEqualTo(startUtc.toInstant());
        assertThat(slot.start().toLocalTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void listing_by_date_includes_new_slots_in_start_order() {
        String lane = GateFixtures.uniqueLane();
        api.createSlot(clerk(), lane, tomorrowAt(11), 2);
        api.createSlot(clerk(), lane, tomorrowAt(8), 2);
        Response r = api.as(fx.newDriver()).queryParam("date", LocalDate.now(GateFixtures.TERMINAL).plusDays(1).toString())
                .queryParam("lane", lane).get("/api/slots");
        assertThatResponse(r).hasStatus(200);
        List<Models.Slot> slots = List.of(r.as(Models.Slot[].class));
        assertThat(slots).extracting(s -> s.start().toLocalTime()).containsExactly(LocalTime.of(8, 0), LocalTime.of(11, 0));
    }

    @Test
    void seeded_lanes_have_slots_for_the_coming_days() {
        Response r = api.rawSlots(fx.newDriver(), LocalDate.now(GateFixtures.TERMINAL).plusDays(2));
        List<Models.Slot> slots = List.of(r.as(Models.Slot[].class));
        assertThat(slots).extracting(Models.Slot::lane).contains("L1", "L2");
    }

    @Test
    void date_is_required_and_must_be_iso() {
        assertThatResponse(api.as(fx.newDriver()).get("/api/slots")).hasStatus(400).hasProblemCode("VALIDATION_FAILED");
        assertThatResponse(api.as(fx.newDriver()).queryParam("date", "20/10/2026").get("/api/slots"))
                .hasStatus(400).hasProblemCode("VALIDATION_FAILED");
    }

    @Test
    void lane_filter_is_validated() {
        Response r = api.as(fx.newDriver()).queryParam("date", "2026-12-01").queryParam("lane", "bad lane!")
                .get("/api/slots");
        assertThatResponse(r).hasStatus(400).hasProblemCode("VALIDATION_FAILED");
    }

    @ParameterizedTest(name = "capacity {0} -> HTTP {1}")
    @CsvSource({"0,400", "1,201", "50,201", "51,400", "-1,400"})
    void capacity_boundaries(int capacity, int status) {
        Response r = api.rawCreateSlot(clerk(), slotBody(GateFixtures.uniqueLane(), tomorrowAt(12), capacity));
        assertThatResponse(r).hasStatus(status);
        if (status == 400) {
            assertThat(r.jsonPath().getList("errors.field", String.class)).contains("capacity");
        }
    }

    @ParameterizedTest(name = "length {0} min -> HTTP {1}")
    @CsvSource({"4,400", "5,201", "240,201", "241,400"})
    void slot_length_boundaries(int minutes, int status) {
        Map<String, Object> body = slotBody(GateFixtures.uniqueLane(), tomorrowAt(13), 1);
        body.put("lengthMinutes", minutes);
        assertThatResponse(api.rawCreateSlot(clerk(), body)).hasStatus(status);
    }

    @Test
    void a_slot_cannot_start_in_the_past() {
        OffsetDateTime past = OffsetDateTime.now(GateFixtures.TERMINAL).minusMinutes(5).truncatedTo(ChronoUnit.MINUTES);
        assertThatResponse(api.rawCreateSlot(clerk(), slotBody(GateFixtures.uniqueLane(), past, 1)))
                .hasStatus(400).hasProblemCode("SLOT_IN_PAST");
    }

    @Test
    void the_same_lane_cannot_have_two_slots_at_the_same_time() {
        String lane = GateFixtures.uniqueLane();
        api.createSlot(clerk(), lane, tomorrowAt(15), 1);
        assertThatResponse(api.rawCreateSlot(clerk(), slotBody(lane, tomorrowAt(15), 1)))
                .hasStatus(400).hasProblemCode("VALIDATION_FAILED");
    }

    @Test
    void lane_format_and_required_fields_are_validated() {
        Map<String, Object> body = new HashMap<>();
        body.put("lane", "lower-case");
        Response r = api.rawCreateSlot(clerk(), body);
        assertThatResponse(r).hasStatus(400).hasProblemCode("VALIDATION_FAILED");
        assertThat(r.jsonPath().getList("errors.field", String.class)).contains("lane", "start", "capacity");
    }

    @Test
    void drivers_cannot_open_slots() {
        assertThatResponse(api.rawCreateSlot(fx.newDriver(), slotBody(GateFixtures.uniqueLane(), tomorrowAt(10), 1)))
                .hasStatus(403).hasProblemCode("FORBIDDEN");
    }

    @Test
    void anonymous_callers_cannot_open_slots() {
        assertThatResponse(api.anonymous().body(slotBody("X1", tomorrowAt(10), 1)).post("/api/slots"))
                .hasStatus(401).hasProblemCode("UNAUTHENTICATED");
    }

    @Test
    void get_by_id_unknown_id_and_bad_id() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(16, 0), 2);
        assertThat(api.slot(fx.newDriver(), slot.id())).isEqualTo(slot);
        assertThatResponse(api.as(fx.newDriver()).get("/api/slots/999999999")).hasStatus(404).hasProblemCode("NOT_FOUND");
        assertThatResponse(api.as(fx.newDriver()).get("/api/slots/abc")).hasStatus(400).hasProblemCode("VALIDATION_FAILED");
    }

    @Test
    void booked_and_remaining_track_bookings() {
        Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(17, 0), 3);
        api.book(fx.newDriver(), slot.id(), GateFixtures.container());
        Models.Slot after = api.slot(fx.newDriver(), slot.id());
        assertThat(after.booked()).isEqualTo(1);
        assertThat(after.remaining()).isEqualTo(2);
    }
}
