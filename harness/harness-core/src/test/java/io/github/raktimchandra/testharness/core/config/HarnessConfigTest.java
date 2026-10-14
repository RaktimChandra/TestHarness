package io.github.raktimchandra.testharness.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HarnessConfigTest {

    @TempDir
    Path dir;
    private ClassLoader loader;

    @BeforeEach
    void writeFiles() throws IOException {
        Files.writeString(dir.resolve("harness-defaults.yml"), """
                ui:
                  browser: chrome
                  explicit-wait: 10s
                api:
                  base-url: http://defaults
                tags: [smoke, api]
                """);
        Files.writeString(dir.resolve("harness.yml"), """
                api:
                  base-url: http://project
                """);
        Files.writeString(dir.resolve("harness-ci.yml"), """
                ui:
                  browser: firefox
                """);
        loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, null);
    }

    private HarnessConfig load(Map<String, String> env, Properties props) {
        return HarnessConfig.load(env, props, loader);
    }

    @Test
    void defaultsApplyInLocalEnvironment() {
        HarnessConfig c = load(Map.of(), new Properties());
        assertThat(c.environment()).isEqualTo(HarnessEnvironment.LOCAL);
        assertThat(c.string("ui.browser")).isEqualTo("chrome");
        assertThat(c.origin("ui.browser")).isEqualTo("harness-defaults.yml");
        assertThat(c.string("tags")).isEqualTo("smoke,api");
    }

    @Test
    void projectFileOverridesFrameworkDefaults() {
        HarnessConfig c = load(Map.of(), new Properties());
        assertThat(c.string("api.base-url")).isEqualTo("http://project");
        assertThat(c.origin("api.base-url")).isEqualTo("harness.yml");
    }

    @Test
    void layersOverrideInOrder() {
        Properties props = new Properties();
        props.setProperty("harness.env", "ci");
        HarnessConfig fileOnly = load(Map.of(), props);
        assertThat(fileOnly.string("ui.browser")).isEqualTo("firefox");

        HarnessConfig withEnv = load(Map.of("HARNESS_UI_BROWSER", "edge"), props);
        assertThat(withEnv.string("ui.browser")).isEqualTo("edge");
        assertThat(withEnv.origin("ui.browser")).isEqualTo("env HARNESS_UI_BROWSER");

        props.setProperty("harness.ui.browser", "chrome");
        HarnessConfig withProp = load(Map.of("HARNESS_UI_BROWSER", "edge"), props);
        assertThat(withProp.string("ui.browser")).isEqualTo("chrome");
        assertThat(withProp.origin("ui.browser")).isEqualTo("-Dharness.ui.browser");
    }

    @Test
    void environmentCanComeFromEnvVar() {
        assertThat(load(Map.of("HARNESS_ENV", "docker"), new Properties()).environment())
                .isEqualTo(HarnessEnvironment.DOCKER);
        assertThat(load(Map.of("HARNESS_ENV", "docker"), new Properties()).asMap()).doesNotContainKey("env");
    }

    @Test
    void unknownEnvironmentIsAClearError() {
        Properties props = new Properties();
        props.setProperty("harness.env", "staging");
        assertThatThrownBy(() -> load(Map.of(), props))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("use local, docker or ci");
    }

    @Test
    void missingKeyExplainsAllThreeWaysToSetIt() {
        assertThatThrownBy(() -> load(Map.of(), new Properties()).string("db.url"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("harness-local.yml")
                .hasMessageContaining("-Dharness.db.url=")
                .hasMessageContaining("HARNESS_DB_URL");
    }

    @Test
    void typedAccessorsAndFallbacks() {
        Properties props = new Properties();
        props.setProperty("harness.workers", "4");
        props.setProperty("harness.headless", "no");
        HarnessConfig c = load(Map.of(), props);
        assertThat(c.integer("workers", 1)).isEqualTo(4);
        assertThat(c.integer("absent", 7)).isEqualTo(7);
        assertThat(c.bool("headless", true)).isFalse();
        assertThat(c.bool("absent", true)).isTrue();
        assertThat(c.duration("ui.explicit-wait", Duration.ZERO)).isEqualTo(Duration.ofSeconds(10));
        assertThat(c.string("absent", "x")).isEqualTo("x");
        assertThat(c.find("absent")).isEmpty();
    }

    @Test
    void invalidValuesNameTheKey() {
        Properties props = new Properties();
        props.setProperty("harness.workers", "four");
        props.setProperty("harness.flag", "maybe");
        HarnessConfig c = load(Map.of(), props);
        assertThatThrownBy(() -> c.integer("workers", 1)).hasMessageContaining("'workers'");
        assertThatThrownBy(() -> c.bool("flag", true)).hasMessageContaining("'flag'");
    }

    @ParameterizedTest(name = "{0} -> {1} ms")
    @CsvSource({"500ms,500", "10s,10000", "2m,120000", "1h,3600000", "PT1.5S,1500", "pt2s,2000"})
    void parsesDurations(String raw, long millis) {
        assertThat(HarnessConfig.parseDuration(raw).toMillis()).isEqualTo(millis);
    }

    @Test
    void rejectsBadDurations() {
        assertThatThrownBy(() -> HarnessConfig.parseDuration("10x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HarnessConfig.parseDuration("s")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secretsComeOnlyFromEnvironmentAndAreRedacted() {
        HarnessConfig c = load(Map.of("GATE_PASSWORD", "hunter2"), new Properties());
        Secret s = c.secret("GATE_PASSWORD", null);
        assertThat(s.reveal()).isEqualTo("hunter2");
        assertThat(s).hasToString("GATE_PASSWORD=****");
        assertThat(c.asMap().values()).doesNotContain("hunter2");
    }

    @Test
    void localSecretsMayFallBackButCiSecretsMayNot() {
        assertThat(load(Map.of(), new Properties()).secret("X_PASS", "dev").reveal()).isEqualTo("dev");
        Properties ci = new Properties();
        ci.setProperty("harness.env", "ci");
        assertThatThrownBy(() -> load(Map.of(), ci).secret("X_PASS", "dev"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("required in CI");
        assertThatThrownBy(() -> load(Map.of(), new Properties()).secret("X_PASS", null))
                .isInstanceOf(ConfigException.class);
    }

    @Test
    void secretEquality() {
        assertThat(new Secret("a", "1")).isEqualTo(new Secret("a", "1")).hasSameHashCodeAs(new Secret("a", "1"));
        assertThat(new Secret("a", "1")).isNotEqualTo(new Secret("a", "2"));
    }

    @Test
    void globalInstanceIsCachedUntilReset() {
        HarnessConfig.reset();
        HarnessConfig first = HarnessConfig.get();
        assertThat(HarnessConfig.get()).isSameAs(first);
        HarnessConfig.reset();
        assertThat(HarnessConfig.get()).isNotSameAs(first);
        assertThat(first.string("ui.explicit-wait")).isEqualTo("10s");
    }

    @Test
    void environmentFileNames() {
        assertThat(HarnessEnvironment.CI.fileName()).isEqualTo("harness-ci.yml");
        assertThat(HarnessEnvironment.parse(null)).isEqualTo(HarnessEnvironment.LOCAL);
        assertThat(HarnessEnvironment.parse(" Docker ")).isEqualTo(HarnessEnvironment.DOCKER);
    }
}
