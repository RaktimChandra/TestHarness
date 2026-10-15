package io.github.raktimchandra.testharness.insights.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RunContextTest {

    @AfterEach
    void clear() {
        System.clearProperty("harness.history.commit");
        System.clearProperty("harness.sut.seeded-bugs");
        HarnessConfig.reset();
    }

    @Test
    void aCleanRunIsIdentifiedByItsCommit() {
        System.setProperty("harness.history.commit", "abc123");
        HarnessConfig.reset();
        assertThat(RunContext.detect(HarnessConfig.get()).commitSha()).isEqualTo("abc123");
    }

    @Test
    void seededBugRunsAreNotTheSameCodeAsTheirCommit() {
        System.setProperty("harness.history.commit", "abc123");
        System.setProperty("harness.sut.seeded-bugs", "OFF_BY_ONE_CAPACITY");
        HarnessConfig.reset();
        assertThat(RunContext.detect(HarnessConfig.get()).commitSha()).isEqualTo("abc123+seeded:OFF_BY_ONE_CAPACITY");
    }
}
