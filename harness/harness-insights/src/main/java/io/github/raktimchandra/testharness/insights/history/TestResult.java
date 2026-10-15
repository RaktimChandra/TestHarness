package io.github.raktimchandra.testharness.insights.history;

import java.time.Instant;

/**
 * One test execution as stored in history.
 *
 * @param key          stable test key ({@link io.github.raktimchandra.testharness.insights.TestKeys})
 * @param displayName  distinguishes invocations of a parameterised test or scenario outline
 * @param failureCause {@link io.github.raktimchandra.testharness.insights.classify.FailureCause} name,
 *                     or null when the test did not fail
 */
public record TestResult(String runId, String commitSha, String branch, String suite, String key, String displayName,
                         Status status, long durationMs, String failureCause, String failureType, String failureMessage,
                         String environment, Instant startedAt) {

    public enum Status { PASSED, FAILED, SKIPPED }

    /** The identity of one test case: key plus display name. */
    public String caseId() {
        return key + " | " + displayName;
    }
}
