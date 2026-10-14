package io.github.raktimchandra.testharness.ui;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.raktimchandra.testharness.core.arch.HarnessArchRules;

@AnalyzeClasses(packages = "io.github.raktimchandra.testharness.ui")
class ArchitectureTest {

    @ArchTest
    static final ArchRule noSleep = HarnessArchRules.NO_THREAD_SLEEP;

    @ArchTest
    static final ArchRule noParking = HarnessArchRules.PARKING_ONLY_IN_PAUSE;

    @ArchTest
    static final ArchRule noStdout = HarnessArchRules.NO_STANDARD_STREAMS;
}
