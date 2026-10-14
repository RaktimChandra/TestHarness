package io.github.raktimchandra.testharness.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.api.schema.SchemaRegistry;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.restassured.response.Response;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResponseAssertTest {

    static StubServer stub;
    static Response ok;
    static Response problem;

    @BeforeAll
    static void start() throws Exception {
        stub = new StubServer().on("GET /things/1", 200,
                "{\"id\":1,\"name\":\"crane\",\"createdAt\":\"2026-10-20T10:00:00Z\"}");
        Properties p = new Properties();
        p.setProperty("harness.api.schema-validation", "lenient");
        HarnessConfig cfg = HarnessConfig.load(Map.of(), p, ResponseAssertTest.class.getClassLoader());
        ok = given(ApiSpecs.base(stub.baseUri(), SchemaRegistry.load("schemas"), cfg)).get("/things/1");
        problem = given(ApiSpecs.base(stub.baseUri(), SchemaRegistry.load("schemas"), cfg)).get("/things/404");
    }

    @AfterAll
    static void stop() {
        stub.close();
    }

    @Test
    void passingAssertionsChain() {
        assertThatResponse(ok).hasStatus(200).hasHeader("X-Stub", "yes").hasJsonValue("name", "crane");
        assertThatResponse(problem).hasStatus(404).hasProblemCode("NOT_FOUND");
    }

    @Test
    void failuresIncludeTheBody() {
        assertThatThrownBy(() -> assertThatResponse(ok).hasStatus(201))
                .hasMessageContaining("Expected HTTP 201 but was 200").hasMessageContaining("crane");
        assertThatThrownBy(() -> assertThatResponse(ok).hasProblemCode("X"))
                .hasMessageContaining("Expected a problem+json response");
        assertThatThrownBy(() -> assertThatResponse(problem).hasProblemCode("SLOT_FULL"))
                .hasMessageContaining("Expected problem code SLOT_FULL but was NOT_FOUND");
        assertThatThrownBy(() -> assertThatResponse(ok).hasHeader("X-Stub", "no"))
                .hasMessageContaining("X-Stub=no");
        assertThatThrownBy(() -> assertThatResponse(ok).hasJsonValue("name", "dock"))
                .hasMessageContaining("$.name = dock");
        assertThatThrownBy(() -> assertThatResponse(ok).hasJsonValue("name", null))
                .hasMessageContaining("$.name = null");
    }
}
