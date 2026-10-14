package io.github.raktimchandra.testharness.api.schema;

import io.github.raktimchandra.testharness.reporting.Report;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Validates <em>every</em> response against the schema registered for its operation.
 * In {@code strict} mode an operation with no registered schema is itself a failure, so a
 * new endpoint cannot slip into the suite unchecked.
 */
public class SchemaValidationFilter implements OrderedFilter {

    public enum Mode { STRICT, LENIENT, OFF;

        public static Mode parse(String value) {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
    }

    private final SchemaRegistry registry;
    private final Mode mode;

    public SchemaValidationFilter(SchemaRegistry registry, Mode mode) {
        this.registry = registry;
        this.mode = mode;
    }

    @Override
    public Response filter(FilterableRequestSpecification req, FilterableResponseSpecification res, FilterContext ctx) {
        Response response = ctx.next(req, res);
        if (mode == Mode.OFF) {
            return response;
        }
        String method = req.getMethod();
        String path = URI.create(req.getURI()).getPath();
        int status = response.statusCode();
        var rule = registry.ruleFor(method, path, status);
        if (rule.isEmpty()) {
            if (mode == Mode.STRICT) {
                throw new SchemaViolation("Schema validation failed: no schema registered for " + method + " " + path
                        + " -> " + status + " (add a rule to registry.yml)");
            }
            return response;
        }
        List<String> errors = registry.validate(rule.get().schema(), response.asString());
        if (!errors.isEmpty()) {
            String detail = String.join("\n  ", errors);
            Report.text("Schema violations (" + rule.get().schema() + ")", detail);
            throw new SchemaViolation("Schema validation failed for " + method + " " + path + " -> " + status
                    + " against " + rule.get().schema() + ":\n  " + detail);
        }
        return response;
    }

    @Override
    public int getOrder() {
        // Run closest to the HTTP call, after reporting/logging filters have recorded the exchange.
        return OrderedFilter.LOWEST_PRECEDENCE;
    }
}
