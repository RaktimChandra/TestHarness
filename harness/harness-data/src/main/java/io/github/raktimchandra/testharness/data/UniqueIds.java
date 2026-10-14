package io.github.raktimchandra.testharness.data;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collision-free identifiers for data that must not clash between parallel tests, or between
 * runs against a shared database: a random 3-character JVM tag plus a per-JVM counter, in
 * upper-case base 36, e.g. {@code next("T", 8)} returns {@code T3K90001}, {@code T3K90002}, ...
 */
public final class UniqueIds {

    private static final int TAG_LENGTH = 3;
    private static final String JVM_TAG = pad(Integer.toString(TestData.random().nextInt(36 * 36 * 36), 36), TAG_LENGTH);
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private UniqueIds() {}

    public static String next(String prefix, int length) {
        int counterWidth = length - prefix.length() - TAG_LENGTH;
        if (counterWidth < 2) {
            throw new IllegalArgumentException("length " + length + " leaves no room for a counter after '" + prefix + "'");
        }
        String counter = Integer.toString(COUNTER.incrementAndGet(), 36);
        if (counter.length() > counterWidth) {
            throw new IllegalStateException("Unique id space for width " + length + " exhausted in this JVM");
        }
        return (prefix + JVM_TAG + pad(counter, counterWidth)).toUpperCase(Locale.ROOT);
    }

    /** Lower-case variant for usernames, e.g. {@code driver-3k9-0007}. */
    public static String username(String role) {
        return (role + "-" + JVM_TAG + "-" + pad(Integer.toString(COUNTER.incrementAndGet(), 36), 4))
                .toLowerCase(Locale.ROOT);
    }

    private static String pad(String s, int n) {
        return s.length() >= n ? s : "0".repeat(n - s.length()) + s;
    }
}
