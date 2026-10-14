package io.github.raktimchandra.testharness.suites.ui;

import io.github.raktimchandra.testharness.core.logging.LoggingContextExtension;
import io.github.raktimchandra.testharness.gate.api.GateApi;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.ui.GatePages;
import io.github.raktimchandra.testharness.ui.junit.BrowserExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** Base for UI journeys: a fresh browser per test, evidence on failure, isolated data. */
@Tag("ui")
@ExtendWith({LoggingContextExtension.class, BrowserExtension.class})
public abstract class UiTest {

    protected final GateApi api = GateApi.get();
    protected final GateFixtures fx = GateFixtures.get();

    protected GatePages pages() {
        return GatePages.current();
    }
}
