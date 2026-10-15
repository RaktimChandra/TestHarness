package io.github.raktimchandra.testharness.insights.select;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;

/**
 * Which descriptors the discovery filters decide on: tests, and containers that stand for one
 * test method. A {@code @ParameterizedTest} or {@code @TestFactory} is a <em>container</em> at
 * discovery time — its invocations only appear during execution — so filtering on
 * {@code isTest()} alone would let every parameterised test through every filter.
 */
final class Decidable {

    private Decidable() {}

    static boolean is(TestDescriptor d) {
        return d.isTest() || d.getSource().filter(MethodSource.class::isInstance).isPresent();
    }
}
