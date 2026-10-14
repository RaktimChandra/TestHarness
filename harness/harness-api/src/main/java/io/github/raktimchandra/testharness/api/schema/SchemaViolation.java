package io.github.raktimchandra.testharness.api.schema;

/**
 * A response did not match its contract. Extends {@link AssertionError} so the test is
 * reported as failed (a product or contract defect), not broken.
 */
public class SchemaViolation extends AssertionError {

    public SchemaViolation(String message) {
        super(message);
    }
}
