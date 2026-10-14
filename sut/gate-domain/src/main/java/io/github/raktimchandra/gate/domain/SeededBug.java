package io.github.raktimchandra.gate.domain;

/**
 * Deliberate, documented defects that can be switched on at runtime. They exist so the test
 * suites can be measured: switch a bug on, run everything, record which suites noticed.
 *
 * <p>Each constant documents where the defect lives and what a correct test must observe.
 */
public enum SeededBug {

    OFF_BY_ONE_CAPACITY(
            "Capacity check uses <= instead of <, so a slot accepts capacity + 1 bookings.",
            "domain: CapacityPolicy"),

    CHECK_DIGIT_EDGE_CASE(
            "ISO 6346 check digit: a weighted-sum remainder of 10 must map to check digit 0;"
                    + " the buggy validator compares the raw remainder, rejecting valid numbers.",
            "domain: ContainerNumber"),

    TIMEZONE_SLOT_TIMES(
            "Slot times are rendered by re-labelling the UTC wall-clock time with the terminal's"
                    + " offset instead of converting, shifting every slot by the zone offset.",
            "domain: SlotTimes"),

    RACE_ON_LAST_SLOT(
            "The capacity check and the insert are not serialised (no row lock), so concurrent"
                    + " requests for the last place in a slot can all succeed.",
            "api: BookingService"),

    MISSING_AUTHORISATION_CHECK(
            "Drivers can read, reschedule and cancel bookings that belong to other drivers (IDOR).",
            "domain: BookingAccessPolicy"),

    API_SKIPS_CONTAINER_VALIDATION(
            "The UI validates the container number but the API accepts any 11-character value.",
            "api: BookingService");

    private final String description;
    private final String location;

    SeededBug(String description, String location) {
        this.description = description;
        this.location = location;
    }

    public String description() {
        return description;
    }

    public String location() {
        return location;
    }
}
