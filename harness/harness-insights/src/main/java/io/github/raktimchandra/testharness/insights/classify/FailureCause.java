package io.github.raktimchandra.testharness.insights.classify;

/** Why a test failed, in the buckets a person triaging a red build actually needs. */
public enum FailureCause {
    /** The element or page structure the test relies on was not there (selector drift). */
    LOCATOR,
    /** Something did not happen in time (slow page, missing event, hung call). */
    TIMEOUT,
    /** The product answered, and the answer was wrong (the interesting kind). */
    ASSERTION,
    /** The test could not run properly: browser, Docker, network, ports, services. */
    ENVIRONMENT,
    /** Test data collided or was invalid (duplicates, constraints, leftovers). */
    DATA,
    /** Any other exception (a bug in the test or the framework, usually). */
    UNKNOWN
}
