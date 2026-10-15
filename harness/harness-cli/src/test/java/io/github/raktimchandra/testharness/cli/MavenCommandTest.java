package io.github.raktimchandra.testharness.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MavenCommandTest {

    private static MavenCommand.Options opts(List<String> suites) {
        return new MavenCommand.Options(suites, null, null, null, null, null, false, null, null, null, null, null,
                false, List.of());
    }

    @Test
    void everythingByDefault() {
        assertThat(MavenCommand.build("./mvnw", opts(List.of()))).containsExactly("./mvnw", "-B", "verify");
    }

    @Test
    void suitesBecomeModulesAndSkipPerfUnlessAsked() {
        assertThat(MavenCommand.build("mvnw", opts(List.of("api", "ui"))))
                .containsSequence("-pl", "suites/api-suite,suites/ui-suite").contains("-DskipPerf=true");
        assertThat(MavenCommand.build("mvnw", opts(List.of("perf")))).doesNotContain("-DskipPerf=true");
    }

    @Test
    void everyOptionMapsToItsProperty() {
        MavenCommand.Options o = new MavenCommand.Options(List.of("ui"), "smoke & !visual", "@smoke", "firefox", "docker",
                "http://localhost:4444", true, "2/4", "sel.txt", "exclude", "OFF_BY_ONE_CAPACITY", 4, true,
                List.of("-o"));
        assertThat(MavenCommand.build("mvnw", o)).contains("-am", "-Dgroups=smoke & !visual",
                "-Dcucumber.filter.tags=@smoke", "-Dharness.ui.browser=firefox", "-Dharness.env=docker",
                "-Dharness.ui.grid-url=http://localhost:4444", "-Dharness.ui.headless=false", "-Dharness.shard=2/4",
                "-Dharness.select=sel.txt", "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Dharness.quarantine.mode=exclude", "-Dharness.sut.seeded-bugs=OFF_BY_ONE_CAPACITY",
                "-Djunit.jupiter.execution.parallel.config.fixed.parallelism=4", "-o");
    }

    @Test
    void unknownSuitesAreRejected() {
        assertThatThrownBy(() -> MavenCommand.build("mvnw", opts(List.of("smoke"))))
                .hasMessageContaining("Unknown suite 'smoke'");
    }
}
