package io.github.raktimchandra.testharness.portsync.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import io.github.raktimchandra.testharness.portsync.Models;
import io.github.raktimchandra.testharness.portsync.Models.BlockSummary;
import io.github.raktimchandra.testharness.portsync.Models.Container;
import io.github.raktimchandra.testharness.portsync.Models.Overview;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Read-side contract and consistency of PortSync's terminal state, across endpoints. */
@DisplayName("PortSync: terminal state is consistent across endpoints")
class TerminalStateTest extends LiveTest {

    @Test
    @DisplayName("The yard invariants hold while the simulation runs")
    void yardInvariantsHold() {
        Models.Invariants report = core.invariants();
        assertThat(report.violations()).as("invariant violations").isEmpty();
        assertThat(report.consistent()).isTrue();
    }

    @Test
    @DisplayName("Overview utilisation equals the blocks' used ÷ capacity, and each block's detail agrees")
    void overviewAndBlocksAgree() {
        Overview overview = core.overview();
        List<BlockSummary> blocks = core.blocks();
        assertThat(blocks).extracting(BlockSummary::block).containsExactly("A", "B", "C", "D");
        long used = blocks.stream().mapToLong(BlockSummary::used).sum();
        long capacity = blocks.stream().mapToLong(BlockSummary::capacity).sum();
        assertThat(overview.yardUtilisation()).isCloseTo((double) used / capacity, within(0.01));
        for (BlockSummary b : blocks) {
            Models.YardBlock detail = core.block(b.block());
            int stacked = detail.stacks().stream().mapToInt(Models.Stack::height).sum();
            assertThat(detail.used()).as("block %s used", b.block()).isEqualTo(stacked);
            assertThat(detail.capacity()).isEqualTo(b.capacity());
            assertThat(detail.stacks()).hasSize(detail.rows() * detail.bays());
        }
    }

    @Test
    @DisplayName("Every container in the yard has a valid ISO 6346 number (independent oracle)")
    void everyStackedContainerNumberIsValid() {
        List<String> numbers = core.blocks().stream().flatMap(b -> core.block(b.block()).stacks().stream())
                .flatMap(s -> s.slots().stream()).map(Models.Slot::containerNumber).filter(n -> n != null).toList();
        assertThat(numbers).isNotEmpty().allMatch(ContainerNumbers::isValid, "valid ISO 6346");
        assertThat(numbers).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Looking up a stacked container returns its slot and the events that put it there")
    void aStackedContainerCanBeLookedUp() {
        String number = containerInYard("A");
        Container c = core.container(number);
        assertThat(c.number()).isEqualTo(number);
        assertThat(c.location()).as("location").isNotNull();
        assertThat(c.location().block()).isEqualTo("A");
        assertThat(c.history()).as("history").isNotEmpty();
    }

    @Test
    @DisplayName("Bad parameters and unknown resources are reported with the ApiError shape and a code")
    void errorsHaveCodes() {
        assertError(core.get("/api/vessels?status=SUNK"), 400, "INVALID_PARAMETER");
        assertError(core.get("/api/yard/blocks/Q"), 404, "BLOCK_NOT_FOUND");
        assertError(core.get("/api/alerts?limit=0"), 400, "INVALID_PARAMETER");
        assertError(core.request().post("/api/alerts/999999999/ack"), 404, "ALERT_NOT_FOUND");
        assertError(core.get("/api/no-such-thing"), 404, "NOT_FOUND");
    }

    private static void assertError(Response r, int status, String code) {
        assertThat(r.statusCode()).as("status of %s", r.asString()).isEqualTo(status);
        assertThat(r.jsonPath().getString("code")).isEqualTo(code);
    }
}
