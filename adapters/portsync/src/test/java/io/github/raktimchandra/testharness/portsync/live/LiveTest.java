package io.github.raktimchandra.testharness.portsync.live;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.core.logging.LoggingContextExtension;
import io.github.raktimchandra.testharness.core.retry.Await;
import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import io.github.raktimchandra.testharness.portsync.CoreApi;
import io.github.raktimchandra.testharness.portsync.Models;
import io.github.raktimchandra.testharness.portsync.PortSync;
import io.github.raktimchandra.testharness.portsync.PortSyncReady;
import io.github.raktimchandra.testharness.portsync.Simulator;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base for tests against a running PortSync stack. Waits once for readiness; every assertion
 * is about PortSync's observable behaviour through its own APIs and UI.
 */
@Tag("portsync")
@ExtendWith(LoggingContextExtension.class)
public abstract class LiveTest {

    protected final CoreApi core = CoreApi.get();
    protected final Simulator simulator = Simulator.get();
    protected static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    @BeforeAll
    static void portSyncIsReady() {
        PortSyncReady.await(PortSync.get());
    }

    protected static Await await(Duration timeout) {
        return Await.atMost(timeout).pollEvery(Duration.ofMillis(500));
    }

    protected static Duration alertTimeout() {
        return HarnessConfig.get().duration("portsync.alert-timeout", Duration.ofSeconds(90));
    }

    /** A valid number with an owner code the simulator never uses, so PortSync has never seen it. */
    protected static String neverSeenNumber(Random random) {
        String prefix = "THX" + "U" + String.format("%06d", random.nextInt(1_000_000));
        return prefix + ContainerNumbers.checkDigit(prefix);
    }

    /** The first container stacked in a yard block, as the core reports it. */
    protected String containerInYard(String block) {
        return core.block(block).stacks().stream().flatMap(s -> s.slots().stream())
                .map(Models.Slot::containerNumber).filter(n -> n != null).findFirst()
                .orElseThrow(() -> new AssertionError("Block " + block + " is empty"));
    }

    /** A crane that is up now, so a test can fault it without fighting another fault. */
    protected String healthyCrane() {
        return core.cranes().stream().filter(c -> !"FAULTED".equals(c.status())).map(Models.Crane::id)
                .reduce((a, b) -> b).orElseThrow(() -> new AssertionError("Every crane is faulted"));
    }
}
