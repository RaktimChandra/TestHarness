# Failed run summary

_Generated 2026-10-20T18:26:05.960580919Z from surefire reports under `/home/runner/work/TestHarness/TestHarness`._

**3 failed test(s)** by cause: {ASSERTION=3}

## Likely cause — DRAFT by openai/qwen2.5:14b (unverified; check before acting)

### Analysis of Failures

#### Shared Root Cause:
The failures share a common pattern where the test expects an HTTP 409 Conflict status code but receives a different status code (e.g., 201 or 200). This suggests that the API endpoint is not behaving as expected under certain conditions, leading to incorrect responses.

### Likely Cause
- **Product Bug**: The Booking API seems to be returning unexpected HTTP status codes when it should enforce capacity limits and reject requests for full slots. This indicates a potential issue in how the API handles concurrency or capacity enforcement logic.
  
### Classification of Failures:
1. **concurrent_requests_for_the_last_place_admit_exactly_one**
   - Expected: 201 (Created) with exactly one successful request
   - Actual: Multiple requests succeeded, leading to an assertion failure.

2. **capacity_is_enforced_at_the_boundary**
   - Expected: 409 Conflict when capacity is full
   - Actual: Received a 201 Created response instead of the expected 409.

3. **to_a_full_slot_is_rejected_and_nothing_changes**
   - Expected: 409 Conflict for rescheduling to a full slot
   - Actual: Received a 200 OK response, indicating no rejection as expected.

### First Thing to Check:
- **API Endpoint Logic**: Review the logic in the Booking API that handles capacity limits and concurrency. Specifically, check how it responds when slots are at their maximum capacity or when concurrent requests occur.
  
- **Test Assertions**: Ensure that test assertions correctly reflect the expected behavior of the API under different conditions (e.g., full capacity, concurrent requests).

### Evidence Insufficiency:
The stack traces do not provide additional context such as logs from the server side or detailed request/response payloads beyond what is already provided. More information would be needed to pinpoint the exact issue in the codebase.

### Conclusion
These failures are likely due to a product bug where the Booking API does not correctly enforce capacity limits and reject requests when slots are full, leading to unexpected HTTP status codes. The first step should be to review the relevant logic in the Booking API and ensure that test assertions accurately reflect expected behavior under various conditions.

## Failures

### Booking API › concurrent_requests_for_the_last_place_admit_exactly_one
- module: `suites/api-suite`
- classified as: **ASSERTION**
- exception: `java.lang.AssertionError`
- message: Expected size: 1 but was: 2 in: [201, 201]

```
java.lang.AssertionError: 

Expected size: 1 but was: 2 in:
[201, 201]
	at io.github.raktimchandra.testharness.suites.api.BookingApiTest.concurrent_requests_for_the_last_place_admit_exactly_one(BookingApiTest.java:131)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.tryRemoveAndExec(ForkJoinPool.java:1351)
	at java.base/java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:422)
	at java.base/java.util.concurrent.ForkJoinTask.join(ForkJoinTask.java:651)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1312)
```

### Booking API › capacity_is_enforced_at_the_boundary
- module: `suites/api-suite`
- classified as: **ASSERTION**
- exception: `java.lang.AssertionError`
- message: Expected HTTP 409 but was 201. Body: {"reference":"GA-SYXHYRP5","status":"BOOKED","direction":"DROP_OFF","containerNumber":"KECU2411162","driver":{"username":"driver-9bc8edbaea","displayName":"Test Driver driver-9bc8edbaea"},"slot":{"id":519,"lane":"TILR0024","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T23:56:00+05:30","updatedAt":"2026-10-20T23:56…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 201.
Body: {"reference":"GA-SYXHYRP5","status":"BOOKED","direction":"DROP_OFF","containerNumber":"KECU2411162","driver":{"username":"driver-9bc8edbaea","displayName":"Test Driver driver-9bc8edbaea"},"slot":{"id":519,"lane":"TILR0024","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T23:56:00+05:30","updatedAt":"2026-10-20T23:56:00+05:30","checkedInAt":null}
	at io.github.raktimchandra.testharness.suites.api.BookingApiTest.capacity_is_enforced_at_the_boundary(BookingApiTest.java:102)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1312)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1843)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1808)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:188)
```

### reschedule › to_a_full_slot_is_rejected_and_nothing_changes
- module: `suites/api-suite`
- classified as: **ASSERTION**
- exception: `java.lang.AssertionError`
- message: Expected HTTP 409 but was 200. Body: {"reference":"GA-6FVVJZ4X","status":"BOOKED","direction":"DROP_OFF","containerNumber":"AHLU9578868","driver":{"username":"driver-e38884d1fe","displayName":"Test Driver driver-e38884d1fe"},"slot":{"id":450,"lane":"TILR0002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T23:55:49+05:30","updatedAt":"2026-10-20T23:55…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 200.
Body: {"reference":"GA-6FVVJZ4X","status":"BOOKED","direction":"DROP_OFF","containerNumber":"AHLU9578868","driver":{"username":"driver-e38884d1fe","displayName":"Test Driver driver-e38884d1fe"},"slot":{"id":450,"lane":"TILR0002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T23:55:49+05:30","updatedAt":"2026-10-20T23:55:49+05:30","checkedInAt":null}
	at io.github.raktimchandra.testharness.suites.api.LifecycleApiTest$Reschedule.to_a_full_slot_is_rejected_and_nothing_changes(LifecycleApiTest.java:55)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.tryRemoveAndExec(ForkJoinPool.java:1351)
	at java.base/java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:422)
	at java.base/java.util.concurrent.ForkJoinTask.join(ForkJoinTask.java:651)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.tryRemoveAndExec(ForkJoinPool.java:1351)
	at java.base/java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:422)
```

