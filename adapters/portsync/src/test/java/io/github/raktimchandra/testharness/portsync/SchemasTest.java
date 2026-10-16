package io.github.raktimchandra.testharness.portsync;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.api.schema.SchemaRegistry;
import io.github.raktimchandra.testharness.api.schema.SchemaRule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs in every build, without PortSync: the adapter's contract is loadable, covers every
 * endpoint the clients call, and accepts and rejects hand-written bodies shaped like PortSync's
 * {@code Views} records (examples, not captured responses).
 */
@DisplayName("PortSync adapter: schema registry (offline)")
class SchemasTest {

    private final SchemaRegistry schemas = PortSync.schemas();

    private String schemaFor(String method, String path, int status) {
        return schemas.ruleFor(method, path, status).map(SchemaRule::schema)
                .orElseThrow(() -> new AssertionError("no rule for " + method + " " + path + " " + status));
    }

    @Test
    void everyEndpointTheClientsCallIsCovered() {
        List<String[]> calls = List.of(
                new String[] {"GET", "/api/overview"}, new String[] {"GET", "/api/cranes"},
                new String[] {"GET", "/api/yard/blocks"}, new String[] {"GET", "/api/yard/blocks/A"},
                new String[] {"GET", "/api/containers/CSQU3054383"},
                new String[] {"GET", "/api/containers/CSQU3054383/validation"},
                new String[] {"GET", "/api/alerts"}, new String[] {"POST", "/api/alerts/7/ack"},
                new String[] {"POST", "/api/alerts/7/resolve"}, new String[] {"GET", "/api/dlq"},
                new String[] {"GET", "/api/events"}, new String[] {"GET", "/api/ops/invariants"},
                new String[] {"POST", "/api/faults/invalid-container"}, new String[] {"POST", "/api/faults/out-of-order"},
                new String[] {"POST", "/api/faults/cranes/QC01/fault"}, new String[] {"GET", "/api/simulation/status"});
        calls.forEach(c -> assertThat(schemaFor(c[0], c[1], 200)).as(c[0] + " " + c[1]).isNotBlank());
        assertThat(schemaFor("GET", "/api/containers/X", 400)).isEqualTo("api-error.json");
        assertThat(schemaFor("POST", "/api/faults/impossible", 400)).isEqualTo("simulator-problem.json");
    }

    @Test
    void aContainerValidationShapedLikePortSyncsPasses() {
        assertThat(schemas.validate("container-validation.json",
                "{\"input\":\"CSQU3054384\",\"valid\":false,\"reason\":\"Check digit mismatch: expected 3 but was 4\","
                        + "\"expectedCheckDigit\":3}")).isEmpty();
    }

    @Test
    void anErrorMustCarryAMachineReadableCode() {
        String ok = "{\"timestamp\":\"2026-10-15T10:00:00Z\",\"status\":404,\"error\":\"Not Found\","
                + "\"code\":\"CONTAINER_NOT_FOUND\",\"message\":\"m\",\"path\":\"/api/containers/X\"}";
        assertThat(schemas.validate("api-error.json", ok)).isEmpty();
        assertThat(schemas.validate("api-error.json", ok.replace(",\"code\":\"CONTAINER_NOT_FOUND\"", "")))
                .anyMatch(v -> v.contains("code"));
    }

    @Test
    void aRenamedFieldIsAContractBreak() {
        String alert = "{\"id\":1,\"rule\":\"CRANE_FAULT_DURATION\",\"entityId\":\"QC01\",\"severity\":\"CRITICAL\","
                + "\"state\":\"OPEN\",\"message\":\"m\",\"observedValue\":16.0,\"threshold\":15.0,"
                + "\"openedAt\":\"2026-10-15T10:00:00Z\",\"lastSeenAt\":null,\"acknowledgedAt\":null,"
                + "\"resolvedAt\":null,\"resolution\":null}";
        assertThat(schemas.validate("alert.json", alert)).isEmpty();
        assertThat(schemas.validate("alert.json", alert.replace("\"state\"", "\"status\""))).isNotEmpty();
        assertThat(schemas.validate("alert.json", alert.replace("\"OPEN\"", "\"CLOSED\""))).isNotEmpty();
    }
}
