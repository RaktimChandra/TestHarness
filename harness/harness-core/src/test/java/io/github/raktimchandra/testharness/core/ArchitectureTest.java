package io.github.raktimchandra.testharness.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.raktimchandra.testharness.core.arch.HarnessArchRules;

@AnalyzeClasses(packages = "io.github.raktimchandra.testharness.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule noSleep = HarnessArchRules.NO_THREAD_SLEEP;

    @ArchTest
    static final ArchRule parkingOnlyInPause = HarnessArchRules.PARKING_ONLY_IN_PAUSE;

    @ArchTest
    static final ArchRule noStandardStreams = HarnessArchRules.NO_STANDARD_STREAMS;
}
