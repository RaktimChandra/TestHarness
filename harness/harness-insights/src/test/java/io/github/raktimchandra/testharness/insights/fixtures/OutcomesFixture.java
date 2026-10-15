package io.github.raktimchandra.testharness.insights.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Launched by the tests (never by surefire): one test per outcome the history must record. */
public class OutcomesFixture {

    @Test
    void passes() {
        assertThat(Calculator.add(2, 2)).isEqualTo(4);
    }

    @Test
    void failsAnAssertion() {
        assertThat(Calculator.add(2, 2)).isEqualTo(5);
    }

    @Test
    void failsToFindAnElement() {
        throw new NoSuchElementException("no such element: [data-testid=\"book-submit\"]");
    }

    @Test
    void isAborted() {
        Assumptions.abort("not on this platform");
    }

    @Disabled("skipped on purpose")
    @Test
    void isDisabled() {
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void parameterised(int n) {
        assertThat(Calculator.twice(n)).isEqualTo(n * 2);
    }
}
