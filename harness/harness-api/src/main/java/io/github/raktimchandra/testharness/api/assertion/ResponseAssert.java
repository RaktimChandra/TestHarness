package io.github.raktimchandra.testharness.api.assertion;

import io.restassured.response.Response;
import org.assertj.core.api.AbstractAssert;

/**
 * Fluent assertions for HTTP responses that print the response body on failure, so a red
 * test explains itself without re-running it.
 *
 * <pre>{@code assertThatResponse(r).hasStatus(409).hasProblemCode("SLOT_FULL");}</pre>
 */
public class ResponseAssert extends AbstractAssert<ResponseAssert, Response> {

    protected ResponseAssert(Response actual) {
        super(actual, ResponseAssert.class);
    }

    public static ResponseAssert assertThatResponse(Response actual) {
        return new ResponseAssert(actual);
    }

    public ResponseAssert hasStatus(int expected) {
        isNotNull();
        if (actual.statusCode() != expected) {
            failWithMessage("Expected HTTP %d but was %d.%nBody: %s", expected, actual.statusCode(), body());
        }
        return this;
    }

    /** Asserts an RFC 9457 problem response with the given machine-readable {@code code}. */
    public ResponseAssert hasProblemCode(String expected) {
        isNotNull();
        String contentType = actual.contentType();
        if (contentType == null || !contentType.startsWith("application/problem+json")) {
            failWithMessage("Expected a problem+json response but Content-Type was %s.%nBody: %s", contentType, body());
        }
        String code = actual.jsonPath().getString("code");
        if (!expected.equals(code)) {
            failWithMessage("Expected problem code %s but was %s.%nBody: %s", expected, code, body());
        }
        return this;
    }

    public ResponseAssert hasHeader(String name, String expected) {
        isNotNull();
        String value = actual.header(name);
        if (!expected.equals(value)) {
            failWithMessage("Expected header %s=%s but was %s", name, expected, value);
        }
        return this;
    }

    public ResponseAssert hasJsonValue(String path, Object expected) {
        isNotNull();
        Object value = actual.jsonPath().get(path);
        if (expected == null ? value != null : !expected.equals(value)) {
            failWithMessage("Expected $.%s = %s but was %s.%nBody: %s", path, expected, value, body());
        }
        return this;
    }

    private String body() {
        String b = actual.asString();
        return b.length() > 2000 ? b.substring(0, 2000) + "…" : b;
    }
}
