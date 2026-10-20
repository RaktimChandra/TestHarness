# Failed run summary

_Generated 2026-10-20T17:58:23.094197134Z from surefire reports under `/home/runner/work/TestHarness/TestHarness`._

**3 failed test(s)** by cause: {ASSERTION=3}

## Likely cause — DRAFT by openai/qwen2.5:1.5b (unverified; check before acting)

### Booking API › concurrent_requests_for_the_last_place_admit_exactly_one

**Root Cause:** **Test Bug**

- **Likely Root Cause:** The test is expecting a specific response (HTTP 409) but the implementation does not handle it correctly. This could be due to an incorrect expectation or a bug in the implementation.

- **Classification:** **ASSERTION**
- **Exception Type:** `java.lang.AssertionError`
- **Message:** Expected size: 1 but was: 2 in:
  [201, 201]
  
**First Thing to Check:**

- Verify that the test is correctly configured and that the expected response (HTTP 409) is being triggered by the implementation.

### Booking API › capacity_is_enforced_at_the_boundary

**Root Cause:** **Test Bug**

- **Likely Root Cause:** The test expects an HTTP 409 but the implementation returns a different status code. This could be due to incorrect expectations or issues in the request handling logic.

- **Classification:** **ASSERTION**
- **Exception Type:** `java.lang.AssertionError`
- **Message:** Expected HTTP 409 but was 201.
  
**First Thing to Check:**

- Ensure that the test is correctly configured and that it matches the expected response (HTTP 409).

### reschedule › to_a_full_slot_is_rejected_and_nothing_changes

**Root Cause:** **Test Bug**

- **Likely Root Cause:** The test expects an HTTP 409 but the implementation returns a different status code. This could be due to incorrect expectations or issues in the request handling logic.

- **Classification:** **ASSERTION**
- **Exception Type:** `java.lang.AssertionError`
- **Message:** Expected HTTP 409 but was 200.
  
**First Thing to Check:**

- Verify that the test is correctly configured and that it matches the expected response (HTTP 409).

### Summary

In all cases, the root cause is likely a bug in either the implementation or the expectations of the test. The first step should be to verify the configuration and ensure that the tests are correctly set up for the expected responses.

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
- message: Expected HTTP 409 but was 201. Body: {"reference":"GA-HGM4TPTK","status":"BOOKED","direction":"DROP_OFF","containerNumber":"HYXU2466063","driver":{"username":"driver-357d2915ee","displayName":"Test Driver driver-357d2915ee"},"slot":{"id":517,"lane":"T97G0022","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T23:28:19+05:30","updatedAt":"2026-10-20T23:28…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 201.
Body: {"reference":"GA-HGM4TPTK","status":"BOOKED","direction":"DROP_OFF","containerNumber":"HYXU2466063","driver":{"username":"driver-357d2915ee","displayName":"Test Driver driver-357d2915ee"},"slot":{"id":517,"lane":"T97G0022","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T23:28:19+05:30","updatedAt":"2026-10-20T23:28:19+05:30","checkedInAt":null}
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
- message: Expected HTTP 409 but was 200. Body: {"reference":"GA-7K5TZ46J","status":"BOOKED","direction":"DROP_OFF","containerNumber":"OTZU7607933","driver":{"username":"driver-42a8f863d9","displayName":"Test Driver driver-42a8f863d9"},"slot":{"id":449,"lane":"T97G0002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T23:28:10+05:30","updatedAt":"2026-10-20T23:28…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 200.
Body: {"reference":"GA-7K5TZ46J","status":"BOOKED","direction":"DROP_OFF","containerNumber":"OTZU7607933","driver":{"username":"driver-42a8f863d9","displayName":"Test Driver driver-42a8f863d9"},"slot":{"id":449,"lane":"T97G0002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T23:28:10+05:30","updatedAt":"2026-10-20T23:28:10+05:30","checkedInAt":null}
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

