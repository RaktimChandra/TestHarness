package io.github.raktimchandra.testharness.core.logging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Identifies one test run. Taken from {@code HARNESS_RUN_ID} (CI sets it to the build id)
 * or generated as {@code yyyyMMdd-HHmmss-xxxx} once per JVM.
 */
public final class RunId {

    private static final String VALUE = resolve();

    private RunId() {}

    public static String value() {
        return VALUE;
    }

    private static String resolve() {
        String fromEnv = System.getenv("HARNESS_RUN_ID");
        String fromProp = System.getProperty("harness.run.id");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        return ts + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
    }
}
