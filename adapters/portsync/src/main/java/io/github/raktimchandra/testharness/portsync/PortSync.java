package io.github.raktimchandra.testharness.portsync;

import io.github.raktimchandra.testharness.api.schema.SchemaRegistry;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.time.Duration;

/**
 * Where the PortSync stack is, read from configuration ({@code portsync.*} in harness.yml,
 * overridable with {@code -Dharness.portsync.api-url=...} or {@code HARNESS_PORTSYNC_API_URL}).
 * The adapter never starts PortSync: it needs Kafka, so it is brought up with the project's
 * own {@code docker compose up}, and the adapter only waits for it ({@link PortSyncReady}).
 */
public record PortSync(String apiUrl, String simulatorUrl, String uiUrl, Duration readyTimeout,
                       long minEventsProcessed) {

    public static final String SCHEMAS = "portsync-schemas";

    public static PortSync fromConfig(HarnessConfig c) {
        return new PortSync(
                trim(c.string("portsync.api-url", "http://localhost:8080")),
                trim(c.string("portsync.simulator-url", "http://localhost:8081")),
                trim(c.string("portsync.ui-url", "http://localhost:3000")),
                c.duration("portsync.ready-timeout", Duration.ofMinutes(4)),
                Long.parseLong(c.string("portsync.min-events-processed", "1500")));
    }

    public static PortSync get() {
        return fromConfig(HarnessConfig.get());
    }

    public static SchemaRegistry schemas() {
        return SchemaRegistry.load(SCHEMAS);
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
