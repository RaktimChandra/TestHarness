package io.github.raktimchandra.testharness.suites.api;

import io.github.raktimchandra.testharness.core.logging.LoggingContextExtension;
import io.github.raktimchandra.testharness.gate.api.GateApi;
import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** Base for API tests: structured logging context, the API client and isolated fixtures. */
@Tag("api")
@ExtendWith(LoggingContextExtension.class)
public abstract class ApiTest {

    protected final GateApi api = GateApi.get();
    protected final GateFixtures fx = GateFixtures.get();

    protected static GateUser clerk() {
        return GateFixtures.seeded("clerk1");
    }
}
