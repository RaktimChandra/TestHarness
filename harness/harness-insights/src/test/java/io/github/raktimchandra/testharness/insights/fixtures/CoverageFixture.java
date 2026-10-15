package io.github.raktimchandra.testharness.insights.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Two tests touching different code, for the per-test coverage recorder. */
public class CoverageFixture {

    @Test
    void adds() {
        assertThat(Calculator.add(1, 1)).isEqualTo(2);
    }

    @Test
    void touchesNothingOfOurs() {
        assertThat("x").hasSize(1);
    }
}
