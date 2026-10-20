# Failed run summary

_Generated 2026-10-20T17:54:45.594944477Z from surefire reports under `/home/runner/work/TestHarness/TestHarness`._

**3 failed test(s)** by cause: {ASSERTION=3}

## Likely cause — DRAFT by openai/qwen2.5:7b (unverified; check before acting)

### Analysis

#### Shared Root Cause and Classification:
- **Shared Root Cause:** The shared root cause appears to be related to incorrect HTTP status codes being returned by the API. Specifically, expected 409 (Conflict) responses are not being received, instead, 201 (Created) or 200 (OK) responses are being returned.
  
- **Classification:**
  - **Product Bug:** The issue seems to be with the Booking API incorrectly handling capacity constraints and rescheduling requests. This suggests a bug in the product logic.

#### Evidence:
- **Booking API › concurrent_requests_for_the_last_place_admit_exactly_one**: Expected size of 1 but received 2.
- **Booking API › capacity_is_enforced_at_the_boundary**: Expected HTTP 409 but received 201 with an unexpected booking response.
- **reschedule › to_a_full_slot_is_rejected_and_nothing_changes**: Expected HTTP 409 but received 200 with a successful booking response.

#### First Thing to Check:
1. **Review the Logic for Handling Capacity Constraints:**
   - Ensure that the logic correctly identifies when capacity is exceeded and returns a 409 status.
   
2. **Check the Rescheduling Logic:**
   - Verify that rescheduling requests are properly rejected with a 409 status.

3. **Inspect Recent Changes:**
   - Look for recent changes in the Booking API or related services that might have altered the behavior of handling capacity and rescheduling.

### Conclusion
The failures most likely share a root cause due to incorrect HTTP status codes being returned by the API, indicating a potential bug in the product logic related to capacity enforcement and rescheduling. The first step should be to review the relevant code and recent changes to identify where the misbehavior is occurring.

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
- message: Expected HTTP 409 but was 201. Body: {"reference":"GA-578CMVHE","status":"BOOKED","direction":"DROP_OFF","containerNumber":"SHBU3961857","driver":{"username":"driver-ee8fc3948a","displayName":"Test Driver driver-ee8fc3948a"},"slot":{"id":504,"lane":"TT0R001P","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T23:24:39+05:30","updatedAt":"2026-10-20T23:24…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 201.
Body: {"reference":"GA-578CMVHE","status":"BOOKED","direction":"DROP_OFF","containerNumber":"SHBU3961857","driver":{"username":"driver-ee8fc3948a","displayName":"Test Driver driver-ee8fc3948a"},"slot":{"id":504,"lane":"TT0R001P","start":"2026-10-21T12:00:00+05:30","end":"2026-10-21T12:30:00+05:30"},"createdAt":"2026-10-20T23:24:39+05:30","updatedAt":"2026-10-20T23:24:39+05:30","checkedInAt":null}
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
- message: Expected HTTP 409 but was 200. Body: {"reference":"GA-68RS03RT","status":"BOOKED","direction":"DROP_OFF","containerNumber":"MWRU7623080","driver":{"username":"driver-10b6d8ec2d","displayName":"Test Driver driver-10b6d8ec2d"},"slot":{"id":449,"lane":"TT0R0001","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T23:24:30+05:30","updatedAt":"2026-10-20T23:24…

```
java.lang.AssertionError: 
Expected HTTP 409 but was 200.
Body: {"reference":"GA-68RS03RT","status":"BOOKED","direction":"DROP_OFF","containerNumber":"MWRU7623080","driver":{"username":"driver-10b6d8ec2d","displayName":"Test Driver driver-10b6d8ec2d"},"slot":{"id":449,"lane":"TT0R0001","start":"2026-10-21T10:30:00+05:30","end":"2026-10-21T11:00:00+05:30"},"createdAt":"2026-10-20T23:24:30+05:30","updatedAt":"2026-10-20T23:24:30+05:30","checkedInAt":null}
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

