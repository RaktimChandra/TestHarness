package io.github.raktimchandra.testharness.portsync.live;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.portsync.Models.Alert;
import io.github.raktimchandra.testharness.portsync.Models.Crane;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The SLA alert lifecycle end to end: a crane fault injected in the simulator becomes a crane
 * state in the core, then a CRANE_FAULT_DURATION alert after 15 simulated minutes, which an
 * operator acknowledges and which clears by itself once the crane is recovered.
 */
@DisplayName("PortSync: a crane fault raises, acknowledges and auto-resolves an SLA alert")
class AlertLifecycleTest extends LiveTest {

    static final String RULE = "CRANE_FAULT_DURATION";

    static Alert openAlertFor(LiveTest t, String crane) {
        return await(alertTimeout()).until("an OPEN " + RULE + " alert for " + crane, () -> t.core.alerts("OPEN"),
                        list -> list.stream().anyMatch(a -> RULE.equals(a.rule()) && crane.equals(a.entityId())))
                .stream().filter(a -> RULE.equals(a.rule()) && crane.equals(a.entityId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("fault → FAULTED → alert OPEN → acknowledged (once only) → recover → RESOLVED automatically")
    void craneFaultAlertLifecycle() {
        String crane = healthyCrane();
        String code = "TESTHARNESS_" + System.currentTimeMillis() % 100_000;
        simulator.faultCrane(crane, code);
        long alertId;
        try {
            Crane faulted = await(EVENT_TIMEOUT).until(crane + " to be FAULTED in the core", () -> core.crane(crane),
                    c -> "FAULTED".equals(c.status()));
            assertThat(faulted.faultCode()).isEqualTo(code);

            Alert open = openAlertFor(this, crane);
            alertId = open.id();
            assertThat(open.severity()).isEqualTo("CRITICAL");
            assertThat(open.message()).contains(crane).contains(code);

            Response ack = core.acknowledge(open.id());
            assertThat(ack.statusCode()).isEqualTo(200);
            assertThat(ack.as(Alert.class).state()).isEqualTo("ACKNOWLEDGED");
            assertThat(ack.as(Alert.class).acknowledgedAt()).isNotNull();

            Response again = core.acknowledge(open.id());
            assertThat(again.statusCode()).as("acknowledging twice").isEqualTo(409);
            assertThat(again.jsonPath().getString("code")).isEqualTo("ALERT_STATE_CONFLICT");
        } finally {
            simulator.recoverCrane(crane);
        }
        await(EVENT_TIMEOUT).until(crane + " to be up again", () -> core.crane(crane),
                c -> !"FAULTED".equals(c.status()));
        Alert resolved = await(alertTimeout()).until("alert " + alertId + " to resolve itself",
                        () -> core.alerts("RESOLVED"), list -> list.stream().anyMatch(a -> a.id() == alertId))
                .stream().filter(a -> a.id() == alertId).findFirst().orElseThrow();
        assertThat(resolved.resolution()).as("who resolved it").isEqualTo("AUTO");
    }
}
