package io.github.raktimchandra.testharness.api;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One structured log line per call; bodies are logged only for 4xx/5xx to keep logs small. */
public class LoggingFilter implements OrderedFilter {

    private static final Logger log = LoggerFactory.getLogger("api");
    private final boolean bodiesOnFailure;

    public LoggingFilter(boolean bodiesOnFailure) {
        this.bodiesOnFailure = bodiesOnFailure;
    }

    @Override
    public Response filter(FilterableRequestSpecification req, FilterableResponseSpecification res, FilterContext ctx) {
        long start = System.nanoTime();
        Response response = ctx.next(req, res);
        long ms = (System.nanoTime() - start) / 1_000_000;
        int status = response.statusCode();
        if (status >= 400 && bodiesOnFailure) {
            log.info("{} {} -> {} ({} ms) {}", req.getMethod(), req.getURI(), status, ms, response.asString());
        } else {
            log.info("{} {} -> {} ({} ms)", req.getMethod(), req.getURI(), status, ms);
        }
        return response;
    }

    @Override
    public int getOrder() {
        return OrderedFilter.HIGHEST_PRECEDENCE;
    }
}
