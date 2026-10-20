# Failed run summary

_Generated 2026-10-20T12:28:48.420675826Z from surefire reports under `/home/runner/work/TestHarness/TestHarness`._

**3 failed test(s)** by cause: {ASSERTION=3}

## Likely cause — DRAFT by openai/qwen2.5:1.5b (unverified; check before acting)

### Booking API › concurrent_requests_for_the_last_place_admit_exactly_one

**Root Cause:** **Product Bug**

**Cause:** The product bug is likely related to the implementation of the `concurrent_requests_for_the_last_place_admit_exactly_one` test. This method expects a specific response size but receives an unexpected number (2 instead of 1). 

**Classification:** Assertion Failure

### Booking API › capacity_is_enforced_at_the_boundary

**Root Cause:** **Test Bug**

**Cause:** The test bug is likely related to the implementation of the `capacity_is_enforced_at_the_boundary` test. This method expects a specific HTTP status code (409) but receives an unexpected one (201). 

**Classification:** Assertion Failure

### reschedule › to_a_full_slot_is_rejected_and_nothing_changes

**Root Cause:** **Environment Problem**

**Cause:** The environment problem is likely related to the timing or configuration of the API. The expected HTTP status code for a `409` conflict was not met, possibly due to an unexpected delay in processing the request.

**Classification:** Assertion Failure

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
- message: Expected HTTP 409 but was 201. Body: {"reference":"GA-JGSE83XX","status":"BOOKED","direction":"DROP_OFF","containerNumber":"BKTU2305852","driver":{"username":"driver-3f99423aff","displayName":"Test Driver driver-3f99423aff"},"slot":{"id":517,"lane":"TIPM0022","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T17:58:43+05:30","updatedAt":"2026-10-20T17:58…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 201.
Body: {"reference":"GA-JGSE83XX","status":"BOOKED","direction":"DROP_OFF","containerNumber":"BKTU2305852","driver":{"username":"driver-3f99423aff","displayName":"Test Driver driver-3f99423aff"},"slot":{"id":517,"lane":"TIPM0022","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T17:58:43+05:30","updatedAt":"2026-10-20T17:58:43+05:30","checkedInAt":null}
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
- message: Expected HTTP 409 but was 200. Body: {"reference":"GA-6EGMAHSS","status":"BOOKED","direction":"DROP_OFF","containerNumber":"MQMU8332067","driver":{"username":"driver-abf0c10ae2","displayName":"Test Driver driver-abf0c10ae2"},"slot":{"id":449,"lane":"TIPM0002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T17:58:31+05:30","updatedAt":"2026-10-20T17:58…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 200.
Body: {"reference":"GA-6EGMAHSS","status":"BOOKED","direction":"DROP_OFF","containerNumber":"MQMU8332067","driver":{"username":"driver-abf0c10ae2","displayName":"Test Driver driver-abf0c10ae2"},"slot":{"id":449,"lane":"TIPM0002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T17:58:31+05:30","updatedAt":"2026-10-20T17:58:31+05:30","checkedInAt":null}
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

