package io.github.raktimchandra.testharness.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.api.schema.SchemaRegistry;
import io.github.raktimchandra.testharness.api.schema.SchemaRule;
import io.github.raktimchandra.testharness.api.schema.SchemaViolation;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SchemaValidationTest {

    static StubServer stub;
    static final SchemaRegistry schemas = SchemaRegistry.load("schemas");

    @BeforeAll
    static void start() throws Exception {
        stub = new StubServer()
                .on("GET /things/1", 200, "{\"id\":1,\"name\":\"crane\",\"createdAt\":\"2026-10-20T10:00:00+05:30\"}")
                .on("GET /things/2", 200, "{\"id\":2,\"name\":\"\",\"createdAt\":\"yesterday\"}")
                .on("GET /things/3", 200, "not json")
                .on("GET /unmapped", 200, "{}")
                .on("GET /page", 200, "<!doctype html><p>not json</p>");
    }

    @AfterAll
    static void stop() {
        stub.close();
    }

    private static HarnessConfig config(String mode) {
        Properties p = new Properties();
        p.setProperty("harness.api.schema-validation", mode);
        return HarnessConfig.load(Map.of(), p, SchemaValidationTest.class.getClassLoader());
    }

    @Test
    void conformingResponsesPass() {
        assertThat(given(ApiSpecs.base(stub.baseUri(), schemas, config("strict"))).get("/things/1").statusCode())
                .isEqualTo(200);
    }

    @Test
    void violationsListEveryProblemIncludingFormats() {
        assertThatThrownBy(() -> given(ApiSpecs.base(stub.baseUri(), schemas, config("strict"))).get("/things/2"))
                .isInstanceOf(SchemaViolation.class)
                .hasMessageContaining("Schema validation failed for GET /things/2 -> 200 against thing.json")
                .hasMessageContaining("/name: must be at least 1 characters long")
                .hasMessageContaining("/createdAt: does not match the date-time pattern");
    }

    @Test
    void invalidJsonIsAViolation() {
        assertThatThrownBy(() -> given(ApiSpecs.base(stub.baseUri(), schemas, config("strict"))).get("/things/3"))
                .isInstanceOf(SchemaViolation.class).hasMessageContaining("not valid JSON");
    }

    @Test
    void strictModeRejectsUnregisteredOperationsLenientDoesNot() {
        assertThatThrownBy(() -> given(ApiSpecs.base(stub.baseUri(), schemas, config("strict"))).get("/unmapped"))
                .isInstanceOf(SchemaViolation.class).hasMessageContaining("no schema registered for GET /unmapped");
        assertThat(given(ApiSpecs.base(stub.baseUri(), schemas, config("lenient"))).get("/unmapped").statusCode())
                .isEqualTo(200);
        assertThat(given(ApiSpecs.base(stub.baseUri(), schemas, config("off"))).get("/things/2").statusCode())
                .isEqualTo(200);
    }

    @Test
    void responsesRegisteredAsNoneAreAcceptedWithoutJsonValidation() {
        assertThat(given(ApiSpecs.base(stub.baseUri(), schemas, config("strict"))).get("/page").statusCode())
                .isEqualTo(200);
    }

    @Test
    void exactStatusRulesWinOverStatusClasses() {
        assertThat(schemas.ruleFor("GET", "/things/9", 404)).get().extracting(SchemaRule::status).isEqualTo("404");
        assertThat(schemas.ruleFor("GET", "/things/9", 409)).get().extracting(SchemaRule::status).isEqualTo("4xx");
        assertThat(schemas.ruleFor("GET", "/things/9/extra", 200)).isEmpty();
        assertThat(schemas.ruleFor("DELETE", "/things/9", 200)).isEmpty();
    }

    @Test
    void pathTemplatesMatchOneSegmentAndQuoteLiterals() {
        SchemaRule rule = new SchemaRule("GET", "/a.b/{id}/c", "200", "x.json");
        assertThat(rule.matches("get", "/a.b/42/c", 200)).isTrue();
        assertThat(rule.matches("GET", "/aXb/42/c", 200)).isFalse();
        assertThat(rule.matches("GET", "/a.b/4/2/c", 200)).isFalse();
    }

    @Test
    void catchAllRulesMatchAnyMethodAndPathButLoseToSpecificOnes() {
        SchemaRule any = new SchemaRule("*", "**", "5xx", "problem.json");
        assertThat(any.matches("PATCH", "/x/y/z", 503)).isTrue();
        assertThat(any.matches("PATCH", "/x/y/z", 404)).isFalse();
        assertThat(new SchemaRule("GET", "/a", "4xx", "p").specificity())
                .isGreaterThan(any.specificity());
        assertThat(new SchemaRule("*", "**", "404", "p").specificity())
                .isGreaterThan(new SchemaRule("GET", "/a", "4xx", "p").specificity());
    }

    @Test
    void registryIsCachedAndMissingRegistriesFail() {
        assertThat(SchemaRegistry.load("schemas")).isSameAs(schemas);
        assertThat(schemas.rules()).hasSize(5);
        assertThatThrownBy(() -> SchemaRegistry.load("nope")).hasMessageContaining("not found");
    }
}
