package io.github.raktimchandra.testharness.portsync;

import static io.restassured.RestAssured.given;

import io.github.raktimchandra.testharness.api.ApiSpecs;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.portsync.Models.Alert;
import io.github.raktimchandra.testharness.portsync.Models.Container;
import io.github.raktimchandra.testharness.portsync.Models.ContainerValidation;
import io.github.raktimchandra.testharness.portsync.Models.Crane;
import io.github.raktimchandra.testharness.portsync.Models.DlqItem;
import io.github.raktimchandra.testharness.portsync.Models.EventItem;
import io.github.raktimchandra.testharness.portsync.Models.Invariants;
import io.github.raktimchandra.testharness.portsync.Models.Overview;
import io.github.raktimchandra.testharness.portsync.Models.YardBlock;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Arrays;
import java.util.List;

/**
 * PortSync core's REST API ({@code portsync-core}, port 8080). Every response passes through the
 * framework's strict schema filter against {@code portsync-schemas/registry.yml}, so a field
 * PortSync renames or retypes fails the test that saw it. Raw methods return the
 * {@link Response} for status and error assertions; typed methods assert 200 first.
 */
public final class CoreApi {

    private final RequestSpecification spec;

    public CoreApi(PortSync portSync, HarnessConfig config) {
        this.spec = ApiSpecs.base(portSync.apiUrl(), PortSync.schemas(), config);
    }

    public static CoreApi get() {
        return new CoreApi(PortSync.get(), HarnessConfig.get());
    }

    public RequestSpecification request() {
        return given().spec(spec);
    }

    public Response get(String path) {
        return request().get(path);
    }

    private <T> T ok(Response r, Class<T> type) {
        if (r.statusCode() != 200) {
            throw new AssertionError("Expected HTTP 200 from " + type.getSimpleName() + " request but was "
                    + r.statusCode() + ": " + r.asString());
        }
        return r.as(type);
    }

    public Overview overview() {
        return ok(get("/api/overview"), Overview.class);
    }

    public List<Crane> cranes() {
        return Arrays.asList(ok(get("/api/cranes"), Crane[].class));
    }

    public Crane crane(String id) {
        return cranes().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("No crane " + id + " in /api/cranes"));
    }

    public List<Models.BlockSummary> blocks() {
        return Arrays.asList(ok(get("/api/yard/blocks"), Models.BlockSummary[].class));
    }

    public YardBlock block(String block) {
        return ok(get("/api/yard/blocks/" + block), YardBlock.class);
    }

    public Response containerResponse(String number) {
        return get("/api/containers/" + number);
    }

    public Container container(String number) {
        return ok(containerResponse(number), Container.class);
    }

    public ContainerValidation validate(String number) {
        return ok(get("/api/containers/" + number + "/validation"), ContainerValidation.class);
    }

    public List<Alert> alerts(String... states) {
        RequestSpecification r = request().queryParam("limit", 1000);
        for (String s : states) {
            r = r.queryParam("state", s);
        }
        return Arrays.asList(ok(r.get("/api/alerts"), Alert[].class));
    }

    public Response acknowledge(long id) {
        return request().post("/api/alerts/" + id + "/ack");
    }

    public Response resolve(long id) {
        return request().post("/api/alerts/" + id + "/resolve");
    }

    public List<DlqItem> deadLetters() {
        return Arrays.asList(ok(request().queryParam("limit", 1000).get("/api/dlq"), DlqItem[].class));
    }

    public List<EventItem> events() {
        return Arrays.asList(ok(request().queryParam("limit", 1000).get("/api/events"), EventItem[].class));
    }

    public Invariants invariants() {
        return ok(get("/api/ops/invariants"), Invariants.class);
    }
}
