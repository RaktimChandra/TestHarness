# Gate API error codes

Every error response is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem
(`Content-Type: application/problem+json`) with a stable, machine-readable `code`.
Tests assert on `code`, never on the human-readable `detail`. The `type` URI of each
problem points to the matching section below.

```json
{
  "type": "https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#slot-full",
  "title": "Conflict",
  "status": 409,
  "detail": "Slot is full (2/2 places taken)",
  "instance": "/api/bookings",
  "code": "SLOT_FULL"
}
```

| code | HTTP | meaning |
|---|---:|---|
| <a id="validation-failed"></a>`VALIDATION_FAILED` | 400 | Body, path or query parameters are missing or malformed. Field-level problems are listed in `errors[]` (`field`, `message`). |
| <a id="invalid-container-number"></a>`INVALID_CONTAINER_NUMBER` | 400 | Not a valid ISO 6346 number: 3-letter owner code, category `U`/`J`/`Z`, 6-digit serial, correct check digit. |
| <a id="slot-in-past"></a>`SLOT_IN_PAST` | 400 | The slot has already started (booking) or would start in the past (creating a slot). |
| <a id="unauthenticated"></a>`UNAUTHENTICATED` | 401 | Missing, invalid or expired bearer token, or wrong username/password (same message for both, so usernames cannot be enumerated). |
| <a id="forbidden"></a>`FORBIDDEN` | 403 | Authenticated, but the role cannot do this (a driver checking in, a clerk booking), or a wrong/missing test-support key. |
| <a id="not-found"></a>`NOT_FOUND` | 404 | Unknown resource — **also** returned when a driver asks for another driver's booking, so the API does not confirm that it exists. |
| `METHOD_NOT_ALLOWED` | 405 | The path exists but not with this HTTP method. |
| <a id="slot-full"></a>`SLOT_FULL` | 409 | No places left in the slot. |
| <a id="duplicate-active-booking"></a>`DUPLICATE_ACTIVE_BOOKING` | 409 | The container already has an open (BOOKED) appointment. |
| <a id="invalid-status-transition"></a>`INVALID_STATUS_TRANSITION` | 409 | Only BOOKED appointments can be rescheduled, cancelled or checked in. |
| <a id="change-cutoff-passed"></a>`CHANGE_CUTOFF_PASSED` | 409 | Reschedule/cancel is closed: the slot starts within the next hour. |
| <a id="checkin-too-early"></a>`CHECKIN_TOO_EARLY` | 409 | Check-in opens 2 hours before the slot starts. |
| <a id="checkin-too-late"></a>`CHECKIN_TOO_LATE` | 409 | Check-in closed 30 minutes after the slot ended. |
| <a id="same-slot"></a>`SAME_SLOT` | 409 | A reschedule to the slot the booking is already in. |
| `INTERNAL_ERROR` | 500 | Unexpected server error (logged with the request path). |
