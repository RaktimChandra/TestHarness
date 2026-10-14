package io.github.raktimchandra.testharness.core.arch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

/** Proves the rules actually fire: each fixture class below breaks one rule on purpose. */
class ArchRulesSelfTest {

    static class Sleeper {
        void nap() throws InterruptedException {
            Thread.sleep(10);
        }
    }

    static class Parker {
        void park() {
            LockSupport.parkNanos(10);
        }
    }

    static class Printer {
        void print() {
            System.out.println("x");
        }
    }

    @Test
    void sleepIsDetected() {
        JavaClasses classes = new ClassFileImporter().importClasses(Sleeper.class);
        assertThatThrownBy(() -> HarnessArchRules.NO_THREAD_SLEEP.check(classes)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Thread.sleep");
    }

    @Test
    void parkingOutsidePauseIsDetected() {
        JavaClasses classes = new ClassFileImporter().importClasses(Parker.class);
        assertThatThrownBy(() -> HarnessArchRules.PARKING_ONLY_IN_PAUSE.check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void standardStreamsAreDetected() {
        JavaClasses classes = new ClassFileImporter().importClasses(Printer.class);
        assertThatThrownBy(() -> HarnessArchRules.NO_STANDARD_STREAMS.check(classes))
                .isInstanceOf(AssertionError.class);
    }
}
