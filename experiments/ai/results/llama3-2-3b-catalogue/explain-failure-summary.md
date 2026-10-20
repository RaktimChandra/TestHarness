# Failed run summary

_Generated 2026-10-20T20:07:30.227151123Z from surefire reports under `/home/runner/work/TestHarness/TestHarness`._

**3 failed test(s)** by cause: {ASSERTION=3}

## Likely cause — DRAFT by openai/llama3.2:3b (unverified; check before acting)

Based on the provided failures, it appears that all three tests are asserting specific HTTP status codes or responses from the Booking API. The common thread among these failures is that they are all expecting a certain HTTP status code (409) but receiving another (201).

**Root Cause:** It's likely that the root cause of these failures is a misconfiguration in the API endpoint or a bug in the API itself.

**Classification:**

* `concurrent_requests_for_the_last_place_admit_exactly_one`: Product Bug
* `capacity_is_enforced_at_the_boundary` and `to_a_full_slot_is_rejected_and_nothing_changes`: Test Bug

The reason for this classification is that these tests are asserting specific behavior from the API, which suggests a bug in the API implementation. In contrast, `concurrent_requests_for_the_last_place_admit_exactly_one` seems to be testing a specific scenario that may not be correctly implemented.

**First Thing to Check:**

* Review the Booking API endpoint configurations and ensure that they are correct and consistent across all tests.
* Verify that the expected HTTP status codes (409) are being returned by the API for these specific endpoints.

Note: Without more information about the environment or additional context, it's difficult to provide a more specific answer. However, based on the provided failures, this is the most likely explanation.

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
- message: Expected HTTP 409 but was 201. Body: {"reference":"GA-CBTZ26PW","status":"BOOKED","direction":"DROP_OFF","containerNumber":"RAOU0195603","driver":{"username":"driver-d39b7d236d","displayName":"Test Driver driver-d39b7d236d"},"slot":{"id":518,"lane":"T2W50024","start":"2026-10-22T12:00:00+05:30","end":"2026-10-22T12:30:00+05:30"},"createdAt":"2026-10-21T01:37:25+05:30","updatedAt":"2026-10-21T01:37…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 201.
Body: {"reference":"GA-CBTZ26PW","status":"BOOKED","direction":"DROP_OFF","containerNumber":"RAOU0195603","driver":{"username":"driver-d39b7d236d","displayName":"Test Driver driver-d39b7d236d"},"slot":{"id":518,"lane":"T2W50024","start":"2026-10-22T12:00:00+05:30","end":"2026-10-22T12:30:00+05:30"},"createdAt":"2026-10-21T01:37:25+05:30","updatedAt":"2026-10-21T01:37:25+05:30","checkedInAt":null}
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
- message: Expected HTTP 409 but was 200. Body: {"reference":"GA-KWH8DB4K","status":"BOOKED","direction":"DROP_OFF","containerNumber":"GUWU9345481","driver":{"username":"driver-22a1d24e76","displayName":"Test Driver driver-22a1d24e76"},"slot":{"id":449,"lane":"T2W50002","start":"2026-10-22T10:30:00+05:30","end":"2026-10-22T11:00:00+05:30"},"createdAt":"2026-10-21T01:37:15+05:30","updatedAt":"2026-10-21T01:37…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 200.
Body: {"reference":"GA-KWH8DB4K","status":"BOOKED","direction":"DROP_OFF","containerNumber":"GUWU9345481","driver":{"username":"driver-22a1d24e76","displayName":"Test Driver driver-22a1d24e76"},"slot":{"id":449,"lane":"T2W50002","start":"2026-10-22T10:30:00+05:30","end":"2026-10-22T11:00:00+05:30"},"createdAt":"2026-10-21T01:37:15+05:30","updatedAt":"2026-10-21T01:37:15+05:30","checkedInAt":null}
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

