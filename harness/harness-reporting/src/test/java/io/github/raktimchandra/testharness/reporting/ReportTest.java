package io.github.raktimchandra.testharness.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Report must behave as a plain call-through even with no Allure test context active. */
class ReportTest {

    @Test
    void stepReturnsTheValueAndPropagatesFailures() {
        assertThat(Report.step("compute", () -> 21 * 2)).isEqualTo(42);
        assertThatThrownBy(() -> Report.step("explode", (Runnable) () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");
    }

    @Test
    void attachmentsAndNotesDoNotThrowOutsideATest() {
        Report.note("hello");
        Report.parameter("browser", "chrome");
        Report.text("t", "x");
        Report.text("null", null);
        Report.json("j", "{}");
        Report.html("h", "<p/>");
        Report.png("p", new byte[] {1, 2});
    }
}
