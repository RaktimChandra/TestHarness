package io.github.raktimchandra.testharness.insights;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;

class TestKeysTest {

    @Test
    void jupiterAndCucumberSourcesGetStableKeys() {
        assertThat(TestKeys.of(MethodSource.from("a.B", "c", "int"))).contains("a.B#c");
        assertThat(TestKeys.of(ClassSource.from("a.B"))).contains("a.B");
        assertThat(TestKeys.of(ClasspathResourceSource.from("/features/x.feature", FilePosition.from(12, 3))))
                .contains("features/x.feature:12");
        assertThat(TestKeys.of(FileSource.from(new File("/repo/suites/bdd/src/test/resources/features/x.feature"),
                FilePosition.from(7)))).contains("features/x.feature:7");
    }

    @Test
    void ownerIsTheClassOrFeature() {
        assertThat(TestKeys.owner("a.B#c")).isEqualTo("a.B");
        assertThat(TestKeys.owner("features/x.feature:12")).isEqualTo("features/x.feature");
        assertThat(TestKeys.owner("a.B")).isEqualTo("a.B");
    }
}
