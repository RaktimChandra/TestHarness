package io.github.raktimchandra.testharness.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllureRunMetadataTest {

    @TempDir
    Path dir;

    private static HarnessConfig config(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return HarnessConfig.load(Map.of(), p, AllureRunMetadataTest.class.getClassLoader());
    }

    @Test
    void writesEnvironmentCategoriesAndExecutor() throws IOException {
        Map<String, String> env = Map.of("GITHUB_SERVER_URL", "https://github.com", "GITHUB_REPOSITORY", "o/r",
                "GITHUB_RUN_ID", "42", "GITHUB_WORKFLOW", "ci", "GITHUB_RUN_NUMBER", "7", "GITHUB_SHA", "abc");
        AllureRunMetadata.write(dir, config("harness.sut.base-url", "http://sut", "harness.ui.browser", "firefox"),
                env::get);
        String props = Files.readString(dir.resolve("environment.properties"));
        assertThat(props).contains("Harness.Env=local", "SUT.BaseUrl=http://sut", "UI.Browser=firefox",
                "Git.Commit=abc", "Java=");
        assertThat(Files.readString(dir.resolve("categories.json"))).contains("Locator broken", "Timeout",
                "Environment / infrastructure", "Product defect (assertion)");
        assertThat(Files.readString(dir.resolve("executor.json")))
                .contains("\"buildUrl\" : \"https://github.com/o/r/actions/runs/42\"", "ci #7");
    }

    @Test
    void noExecutorOutsideCi() {
        AllureRunMetadata.write(dir, config(), k -> null);
        assertThat(dir.resolve("executor.json")).doesNotExist();
        assertThat(dir.resolve("environment.properties")).exists();
    }

    @Test
    void categoriesAreOrderedMostSpecificFirst() {
        assertThat(AllureRunMetadata.categories()).extracting(c -> c.get("name")).first()
                .isEqualTo("Locator broken");
        assertThat(AllureRunMetadata.categories()).extracting(c -> c.get("name")).last()
                .isEqualTo("Test defect (unexpected exception)");
    }
}
