package io.github.raktimchandra.testharness.ui.a11y;

/** Test access to the scanner's package-private target formatting. */
public final class A11yScannerTargets {

    private A11yScannerTargets() {
    }

    public static String of(Object axeTarget) {
        return A11yScanner.target(axeTarget);
    }
}
