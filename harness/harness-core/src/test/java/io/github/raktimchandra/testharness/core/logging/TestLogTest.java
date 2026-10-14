package io.github.raktimchandra.testharness.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TestLogTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void withRestoresThePreviousContext() {
        TestLog.put("browser", "chrome");
        TestLog.with(Map.of("browser", "firefox", "step", "login"), () -> {
            assertThat(TestLog.snapshot()).containsEntry("browser", "firefox").containsEntry("step", "login");
        });
        assertThat(TestLog.snapshot()).containsExactly(Map.entry("browser", "chrome"));
    }

    @Test
    void withClearsWhenThereWasNoContext() {
        TestLog.with(Map.of("a", "1"), () -> assertThat(TestLog.snapshot()).containsKey("a"));
        assertThat(TestLog.snapshot()).isEmpty();
    }

    @Test
    void nullRemoves() {
        TestLog.put("k", "v");
        TestLog.put("k", null);
        assertThat(TestLog.snapshot()).doesNotContainKey("k");
    }

    @Test
    void runIdIsStableAndShaped() {
        assertThat(RunId.value()).isEqualTo(RunId.value()).isNotBlank();
    }
}
