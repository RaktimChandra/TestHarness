| Seeded bug | contract | api | security | ui | a11y | visual | bdd | perf |
|---|---|---|---|---|---|---|---|---|
| `API_SKIPS_CONTAINER_VALIDATION` | escaped 0/9 | **caught** 4/85 | escaped 0/17 | escaped 0/17 | escaped 0/3 | escaped 0/5 | **caught** 2/15 | escaped 0/0 |
| `CHECK_DIGIT_EDGE_CASE` | **caught** 1/9 | **caught** 4/85 | **caught** 1/17 | **caught** 1/17 | escaped 0/3 | escaped 0/5 | **caught** 4/15 | escaped 0/0 |
| `MISSING_AUTHORISATION_CHECK` | escaped 0/9 | **caught** 3/85 | **caught** 1/17 | escaped 0/17 | escaped 0/3 | escaped 0/5 | **caught** 1/15 | escaped 0/0 |
| `OFF_BY_ONE_CAPACITY` | **caught** 1/9 | **caught** 3/85 | escaped 0/17 | escaped 0/17 | escaped 0/3 | escaped 0/5 | **caught** 1/15 | escaped 0/0 |
| `RACE_ON_LAST_SLOT` | escaped 0/9 | **caught** 1/85 | escaped 0/17 | escaped 0/17 | escaped 0/3 | escaped 0/5 | escaped 0/15 | escaped 0/0 |
| `TIMEZONE_SLOT_TIMES` | escaped 0/9 | **caught** 6/85 | escaped 0/17 | **caught** 3/17 | escaped 0/3 | escaped 0/5 | **caught** 1/15 | escaped 0/0 |
| **detection rate** | 33 % | 100 % | 33 % | 33 % | 0 % | 0 % | 83 % | 0 % |

Baseline (no bug): contract 0/9 failed (exit 0), api 0/85 failed (exit 0), security 0/17 failed (exit 0), ui 0/17 failed (exit 0), a11y 0/3 failed (exit 0), visual 0/5 failed (exit 0), bdd 0/15 failed (exit 0), perf 0/0 failed (exit 0)

## Failing tests per bug

### API_SKIPS_CONTAINER_VALIDATION
- api: Booking API > container_number_rules_are_enforced_by_the_api(ContainerCase)[5] — AssertionError: Expected HTTP 400 but was 201.
- api: Booking API > container_number_rules_are_enforced_by_the_api(ContainerCase)[7] — AssertionError: Expected HTTP 400 but was 201.
- api: Booking API > container_number_rules_are_enforced_by_the_api(ContainerCase)[10] — AssertionError: Expected HTTP 400 but was 201.
- api: Booking API > container_number_rules_are_enforced_by_the_api(ContainerCase)[6] — AssertionError: Expected HTTP 400 but was 201.
- bdd: RunBddSuite > Example #1.4 — AssertionError: [HTTP status]
- bdd: RunBddSuite > Example #1.3 — AssertionError: [HTTP status]

### CHECK_DIGIT_EDGE_CASE
- contract: PactProviderTest > interactionHonoursTheContract(PactVerificationContext, HttpRequest)[1] — AssertionError: gate-ui - Upon a booking for a full slot
- api: Booking API > a_generated_remainder_ten_container_is_accepted — AssertionError: Expected HTTP 201 but was 400.
- api: Booking API > container_number_rules_are_enforced_by_the_api(ContainerCase)[2] — AssertionError: Expected HTTP 201 but was 400.
- api: cancel > another_driver_cannot_cancel_it — AssertionError: Expected HTTP 201 but got 400: {"type":"https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#invalid-container-number","title":"Bad Request","status":400,"detail":"Check digit should be 0 but was 0","instance":"/api/bookings","code":"INVALID_CONTAINER_NUMBER"}
- api: cancel > clerks_cannot_cancel_and_unknown_references_are_404 — AssertionError: Expected HTTP 201 but got 400: {"type":"https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#invalid-container-number","title":"Bad Request","status":400,"detail":"Check digit should be 0 but was 0","instance":"/api/bookings","code":"INVALID_CONTAINER_NUMBER"}
- security: Security smoke > a_driver_cannot_read_change_or_cancel_another_drivers_booking — AssertionError: Expected HTTP 201 but got 400: {"type":"https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#invalid-container-number","title":"Bad Request","status":400,"detail":"Check digit should be 0 but was 0","instance":"/api/bookings","code":"INVALID_CONTAINER_NUMBER"}
- ui: Booking journey > a_remainder_ten_container_number_is_accepted_end_to_end — TimeoutException: Expected condition failed: waiting 10000 ms for By.cssSelector: [data-testid="driver-notice"] to contain text 'Booked '
- bdd: RunBddSuite > A clerk checks a truck in from the gate screen — AssertionError: Expected HTTP 201 but got 400: {"type":"https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#invalid-container-number","title":"Bad Request","status":400,"detail":"Check digit should be 0 but was 0","instance":"/api/bookings","code":"INVALID_CONTAINER_NUMBER"}
- bdd: RunBddSuite > Drivers cannot check themselves in — AssertionError: Expected HTTP 201 but got 400: {"type":"https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#invalid-container-number","title":"Bad Request","status":400,"detail":"Check digit should be 0 but was 0","instance":"/api/bookings","code":"INVALID_CONTAINER_NUMBER"}
- bdd: RunBddSuite > Check-in is refused before the window opens — AssertionError: Expected HTTP 201 but got 400: {"type":"https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#invalid-container-number","title":"Bad Request","status":400,"detail":"Check digit should be 0 but was 0","instance":"/api/bookings","code":"INVALID_CONTAINER_NUMBER"}
- bdd: RunBddSuite > Example #1.2 — AssertionError: Expected HTTP 201 but was 400.

### MISSING_AUTHORISATION_CHECK
- api: reschedule > another_driver_cannot_reschedule_it — AssertionError: Expected HTTP 404 but was 200.
- api: cancel > another_driver_cannot_cancel_it — AssertionError: Expected HTTP 404 but was 200.
- api: Search and object-level access > a_driver_cannot_read_another_drivers_booking — AssertionError: Expected HTTP 404 but was 200.
- security: Security smoke > a_driver_cannot_read_change_or_cancel_another_drivers_booking — AssertionError: Expected HTTP 404 but was 200.
- bdd: RunBddSuite > Drivers cannot see each other's bookings — AssertionError: Expected HTTP 404 but was 200.

### OFF_BY_ONE_CAPACITY
- contract: PactProviderTest > interactionHonoursTheContract(PactVerificationContext, HttpRequest)[1] — AssertionError: gate-ui - Upon a booking for a full slot
- api: Booking API > concurrent_requests_for_the_last_place_admit_exactly_one — AssertionError: Expected size: 1 but was: 2 in:
- api: Booking API > capacity_is_enforced_at_the_boundary — AssertionError: Expected HTTP 409 but was 201.
- api: reschedule > to_a_full_slot_is_rejected_and_nothing_changes — AssertionError: Expected HTTP 409 but was 200.
- bdd: RunBddSuite > A slot cannot take more trucks than its capacity — AssertionError: [HTTP status]

### RACE_ON_LAST_SLOT
- api: Booking API > concurrent_requests_for_the_last_place_admit_exactly_one — AssertionError: Expected size: 1 but was: 6 in:

### TIMEZONE_SLOT_TIMES
- api: Auth API > seeded_users_log_in_and_receive_a_bearer_token(String, String)[1] — AssertionError: Expecting actual:
- api: Auth API > seeded_users_log_in_and_receive_a_bearer_token(String, String)[2] — AssertionError: Expecting actual:
- api: Booking API > the_booking_reports_the_slot_time_exactly_as_the_slot_does — AssertionFailedError: expected: 10:30
- api: Slots API > listing_by_date_includes_new_slots_in_start_order — AssertionFailedError: Expecting actual:
- api: Slots API > a_slot_requested_in_utc_is_reported_in_terminal_time — AssertionFailedError: expected: 2026-10-16T08:30:00Z
- api: Slots API > slot_times_are_the_terminal_wall_clock_with_its_offset — AssertionFailedError: [same instant as requested]
- ui: Booking journey > the_slot_table_shows_the_terminal_time_and_live_remaining_places — AssertionFailedError: expected: "14:30–15:00"
- ui: Booking journey > a_driver_books_a_slot_and_sees_it_in_their_bookings — AssertionError: Expecting actual:
- ui: Reschedule, cancel and check-in journeys > a_driver_reschedules_to_another_slot — AssertionFailedError: Expecting value to be true but was false
- bdd: RunBddSuite > A driver books from the booking screen — AssertionError: Expecting actual:

