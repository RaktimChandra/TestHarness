# Failed run summary

_Generated 2026-10-20T13:24:38.607178376Z from surefire reports under `/home/runner/work/TestHarness/TestHarness`._

**3 failed test(s)** by cause: {ASSERTION=3}

## Likely cause — DRAFT by openai/llama3.2:3b (unverified; check before acting)

**Root Cause:** The root cause of these failures is likely a concurrency issue in the Booking API. Specifically, it appears that multiple requests are being processed simultaneously, causing the assertions to fail.

**Likelihood of Product Bug:** Low (but possible)

**Likelihood of Test Bug:** Medium

**Likelihood of Environment Problem:** High

The fact that all three tests are failing with similar error messages suggests a concurrency issue. The Booking API is not designed to handle concurrent requests efficiently, which may lead to inconsistent behavior.

**First Thing to Check:**

1. **Concurrency Control Mechanism**: Review the Booking API's concurrency control mechanism to ensure it is properly implemented and configured to handle multiple requests simultaneously.
2. **Locking or Synchronization**: Investigate if the API uses locking or synchronization mechanisms to prevent concurrent access to shared resources, such as booking slots.

**Insufficient Evidence:**

* Without more information about the test environment, network conditions, or specific request patterns, it's difficult to determine the exact cause of the concurrency issue.
* The stack traces do not provide enough context to identify a specific line of code or method that might be causing the problem.

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
- message: Expected HTTP 409 but was 201. Body: {"reference":"GA-HHAYJ5D5","status":"BOOKED","direction":"DROP_OFF","containerNumber":"OEUU4701159","driver":{"username":"driver-387bb07c01","displayName":"Test Driver driver-387bb07c01"},"slot":{"id":506,"lane":"TQEQ001R","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T18:54:34+05:30","updatedAt":"2026-10-20T18:54…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 201.
Body: {"reference":"GA-HHAYJ5D5","status":"BOOKED","direction":"DROP_OFF","containerNumber":"OEUU4701159","driver":{"username":"driver-387bb07c01","displayName":"Test Driver driver-387bb07c01"},"slot":{"id":506,"lane":"TQEQ001R","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T18:54:34+05:30","updatedAt":"2026-10-20T18:54:34+05:30","checkedInAt":null}
	at io.github.raktimchandra.testharness.suites.api.BookingApiTest.capacity_is_enforced_at_the_boundary(BookingApiTest.java:102)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
	at java.base/java.util.concurrent.ForkJoinPool.helpJoin(ForkJoinPool.java:2076)
	at java.base/java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:423)
	at java.base/java.util.concurrent.ForkJoinTask.join(ForkJoinTask.java:651)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1312)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1843)
```

### reschedule › to_a_full_slot_is_rejected_and_nothing_changes
- module: `suites/api-suite`
- classified as: **ASSERTION**
- exception: `java.lang.AssertionError`
- message: Expected HTTP 409 but was 200. Body: {"reference":"GA-GQKR5NTK","status":"BOOKED","direction":"DROP_OFF","containerNumber":"UPPU0447092","driver":{"username":"driver-e972e4263f","displayName":"Test Driver driver-e972e4263f"},"slot":{"id":449,"lane":"TQEQ0001","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T18:54:27+05:30","updatedAt":"2026-10-20T18:54…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 200.
Body: {"reference":"GA-GQKR5NTK","status":"BOOKED","direction":"DROP_OFF","containerNumber":"UPPU0447092","driver":{"username":"driver-e972e4263f","displayName":"Test Driver driver-e972e4263f"},"slot":{"id":449,"lane":"TQEQ0001","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T18:54:27+05:30","updatedAt":"2026-10-20T18:54:27+05:30","checkedInAt":null}
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

