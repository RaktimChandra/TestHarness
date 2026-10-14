package io.github.raktimchandra.testharness.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Architecture rules every TestHarness module (and every suite) runs against itself.
 *
 * <p>Fixed sleeps are the most common source of slow and flaky UI tests, so they are banned
 * outright; tests must wait on explicit conditions. The single sanctioned blocking primitive
 * is {@code core.retry.Pause}, used by bounded retry back-off and condition polling.
 */
public final class HarnessArchRules {

    private static final String PAUSE = "io.github.raktimchandra.testharness.core.retry.Pause";

    private HarnessArchRules() {}

    public static final ArchRule NO_THREAD_SLEEP = noClasses()
            .should().callMethod(Thread.class, "sleep", long.class)
            .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
            .orShould().callMethod(Thread.class, "sleep", java.time.Duration.class)
            .orShould().callMethod(TimeUnit.class, "sleep", long.class)
            .because("tests must wait on explicit conditions, never for a fixed time");

    public static final ArchRule PARKING_ONLY_IN_PAUSE = noClasses()
            .that(DescribedPredicate.not(nameStartsWith(PAUSE)))
            .should().callMethodWhere(target("park", LockSupport.class))
            .because("core.retry.Pause is the one audited place allowed to block for a fixed time");

    public static final ArchRule NO_STANDARD_STREAMS = noClasses()
            .should().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .because("output goes through SLF4J so it is structured and attached to reports");

    private static DescribedPredicate<JavaClass> nameStartsWith(String prefix) {
        return DescribedPredicate.describe("name starts with " + prefix, c -> c.getName().startsWith(prefix));
    }

    private static DescribedPredicate<JavaMethodCall> target(String namePrefix, Class<?> owner) {
        return DescribedPredicate.describe(owner.getSimpleName() + "." + namePrefix + "*",
                call -> call.getTargetOwner().isEquivalentTo(owner) && call.getName().startsWith(namePrefix));
    }
}
