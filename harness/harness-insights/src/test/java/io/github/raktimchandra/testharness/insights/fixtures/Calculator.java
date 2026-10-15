package io.github.raktimchandra.testharness.insights.fixtures;

/** Production-like code the fixtures exercise, so per-test coverage has something to find. */
public final class Calculator {
    private Calculator() {}

    public static int add(int a, int b) {
        return a + b;
    }

    public static int twice(int n) {
        return Doubler.apply(n);
    }

    static final class Doubler {
        private Doubler() {}

        static int apply(int n) {
            return n * 2;
        }
    }
}
