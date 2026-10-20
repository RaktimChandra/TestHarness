# Failed run summary

_Generated 2026-10-20T14:51:01.537615169Z from surefire reports under `/home/runner/work/TestHarness/TestHarness`._

**3 failed test(s)** by cause: {ASSERTION=3}

## Likely cause — DRAFT by openai/qwen2.5:7b (unverified; check before acting)

### Analysis

#### Shared Root Cause and Likely Cause:
- **Shared Root Cause:** The root cause likely lies in the Booking API's handling of HTTP status codes, specifically for `409` (Conflict) responses.
- **Likely Cause:** There might be a misconfiguration or bug in how the API handles conflict scenarios. This could result in unexpected successful (`200`) responses instead of expected `409`.

#### Classification:
- **First Failure: concurrent_requests_for_the_last_place_admit_exactly_one** - Test Bug
  - The test is expecting exactly one request to be admitted, but two were admitted.
  
- **Second and Third Failures:** 
  - **capacity_is_enforced_at_the_boundary**
  - **to_a_full_slot_is_rejected_and_nothing_changes**
  - Both are classified as product bugs. They both expect `409` responses but receive `200`.

#### Environment Problem:
- The environment seems stable, as the failures are related to expected HTTP status codes not being met.

### First Thing to Check:
1. **Review API Logic for Conflict Handling:**
   - Specifically check the logic in the Booking API that handles `409` responses.
   - Ensure that all conflict scenarios correctly return `409`.

2. **Test Configuration and Data:**
   - Verify if there are any specific test configurations or data points that might be causing these unexpected successful responses.

3. **Logs and Database:**
   - Check the logs around the time of the failed tests to see if there were any unusual operations or changes in the database state.
   - Look for any recent updates or deployments that might have affected the API's behavior.

### Conclusion:
The shared root cause is likely a bug in how the Booking API handles `409` responses, leading to unexpected successful (`200`) responses. This needs to be fixed by reviewing and correcting the logic responsible for conflict handling.

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
- message: Expected HTTP 409 but was 201. Body: {"reference":"GA-725ZSFC3","status":"BOOKED","direction":"DROP_OFF","containerNumber":"TFVU4432367","driver":{"username":"driver-803aab5f8d","displayName":"Test Driver driver-803aab5f8d"},"slot":{"id":517,"lane":"T1640022","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T20:20:56+05:30","updatedAt":"2026-10-20T20:20…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 201.
Body: {"reference":"GA-725ZSFC3","status":"BOOKED","direction":"DROP_OFF","containerNumber":"TFVU4432367","driver":{"username":"driver-803aab5f8d","displayName":"Test Driver driver-803aab5f8d"},"slot":{"id":517,"lane":"T1640022","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T20:20:56+05:30","updatedAt":"2026-10-20T20:20:56+05:30","checkedInAt":null}
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
- message: Expected HTTP 409 but was 200. Body: {"reference":"GA-P11YR1SC","status":"BOOKED","direction":"DROP_OFF","containerNumber":"RORU0718598","driver":{"username":"driver-5f214177c4","displayName":"Test Driver driver-5f214177c4"},"slot":{"id":449,"lane":"T1640002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T20:20:44+05:30","updatedAt":"2026-10-20T20:20…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 200.
Body: {"reference":"GA-P11YR1SC","status":"BOOKED","direction":"DROP_OFF","containerNumber":"RORU0718598","driver":{"username":"driver-5f214177c4","displayName":"Test Driver driver-5f214177c4"},"slot":{"id":449,"lane":"T1640002","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T20:20:44+05:30","updatedAt":"2026-10-20T20:20:44+05:30","checkedInAt":null}
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

