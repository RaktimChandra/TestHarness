package io.github.raktimchandra.testharness.api;

import io.github.raktimchandra.testharness.api.schema.SchemaRegistry;
import io.github.raktimchandra.testharness.api.schema.SchemaValidationFilter;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.mapper.factory.Jackson2ObjectMapperFactory;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Builds REST Assured request specifications from configuration. Every spec carries the
 * same filters: Allure (request/response attachments), structured logging and schema
 * validation, so suites never forget to validate a response.
 */
public final class ApiSpecs {

    private ApiSpecs() {}

    /** Jackson used for typed models: java.time support, ISO dates, lenient to new fields. */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    public static RequestSpecification base(String baseUri, SchemaRegistry schemas, HarnessConfig config) {
        Duration connect = config.duration("api.connect-timeout", Duration.ofSeconds(5));
        Duration read = config.duration("api.read-timeout", Duration.ofSeconds(30));
        RestAssuredConfig raConfig = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", (int) connect.toMillis())
                        .setParam("http.socket.timeout", (int) read.toMillis()))
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .jackson2ObjectMapperFactory((Jackson2ObjectMapperFactory) (type, charset) -> MAPPER));
        return new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setConfig(raConfig)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new LoggingFilter(config.bool("api.log-bodies-on-failure", true)))
                .addFilter(new AllureRestAssured())
                .addFilter(new SchemaValidationFilter(schemas,
                        SchemaValidationFilter.Mode.parse(config.string("api.schema-validation", "strict"))))
                .build();
    }
}
