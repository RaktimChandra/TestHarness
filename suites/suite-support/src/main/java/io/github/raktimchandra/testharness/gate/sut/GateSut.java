package io.github.raktimchandra.testharness.gate.sut;

import io.github.raktimchandra.gate.api.GateApplication;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.data.db.PostgresFixture;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The system under test, as the suites see it: a base URL plus the key for its test-support
 * API. Resolved once per JVM, on first use.
 *
 * <ul>
 *   <li>{@code sut.mode=embedded} (default): starts PostgreSQL in Testcontainers and the Gate
 *       application in this JVM on a random port. Still black-box (all access is over HTTP
 *       and a real browser), but it needs no Docker Compose and lets JaCoCo see which SUT code
 *       each test executed (used by test impact analysis in Phase 3).</li>
 *   <li>{@code sut.mode=external}: targets {@code sut.base-url} (e.g. Docker Compose) with the
 *       key from the {@code GATE_TEST_SUPPORT_KEY} environment variable.</li>
 * </ul>
 */
public final class GateSut {

    private static final Logger log = LoggerFactory.getLogger(GateSut.class);
    private static volatile GateSut instance;
    private static volatile RuntimeException startFailure;

    private final String baseUrl;
    private final String testSupportKey;
    private final PostgresFixture database;
    private final ConfigurableApplicationContext app;

    private GateSut(String baseUrl, String testSupportKey, PostgresFixture database,
                    ConfigurableApplicationContext app) {
        this.baseUrl = baseUrl;
        this.testSupportKey = testSupportKey;
        this.database = database;
        this.app = app;
    }

    /**
     * The SUT for this JVM. If start-up fails, the failure is remembered and re-thrown at
     * once to every later caller, so one broken environment produces one clear error rather
     * than a slow retry per test.
     */
    public static GateSut get() {
        GateSut s = instance;
        if (s == null) {
            synchronized (GateSut.class) {
                if (startFailure != null) {
                    throw new IllegalStateException("Gate SUT failed to start earlier in this run; see the first error",
                            startFailure);
                }
                s = instance;
                if (s == null) {
                    try {
                        s = start(HarnessConfig.get());
                    } catch (RuntimeException e) {
                        startFailure = e;
                        throw e;
                    }
                    instance = s;
                }
            }
        }
        return s;
    }

    static GateSut start(HarnessConfig config) {
        String mode = config.string("sut.mode", "embedded");
        if (mode.equals("external")) {
            String url = config.string("sut.base-url");
            String key = config.secret("GATE_TEST_SUPPORT_KEY", "local-test-support-key").reveal();
            log.info("Targeting external Gate SUT at {}", url);
            return new GateSut(stripSlash(url), key, null, null);
        }
        if (!mode.equals("embedded")) {
            throw new IllegalArgumentException("sut.mode must be 'embedded' or 'external', not '" + mode + "'");
        }
        long t0 = System.nanoTime();
        PostgresFixture db = PostgresFixture.shared();
        String key = random(16);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("server.port", config.string("sut.port", "0"));
        props.put("spring.datasource.url", db.jdbcUrl());
        props.put("spring.datasource.username", db.username());
        props.put("spring.datasource.password", db.password());
        props.put("gate.test-support.enabled", "true");
        props.put("gate.test-support.key", key);
        props.put("gate.jwt.secret", random(32));
        String bugs = config.string("sut.seeded-bugs", "").strip();
        if (!bugs.isEmpty()) {
            props.put("gate.seeded-bugs", bugs);
        }
        props.put("spring.main.banner-mode", "off");
        props.put("management.endpoints.web.exposure.include", "health,info");
        // Passed as command-line arguments: those outrank application.yml, whereas
        // SpringApplicationBuilder.properties(...) would only set (lowest-precedence) defaults.
        String[] args = props.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(GateApplication.class)
                .web(WebApplicationType.SERVLET)
                .logStartupInfo(false)
                .run(args);
        int port = ((WebServerApplicationContext) ctx).getWebServer().getPort();
        String url = "http://localhost:" + port;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> SpringApplication.exit(ctx), "gate-sut-shutdown"));
        log.info("Embedded Gate SUT up at {} in {} ms (seeded bugs: {})", url, (System.nanoTime() - t0) / 1_000_000,
                bugs.isEmpty() ? "none" : bugs);
        System.setProperty("harness.sut.base-url", url);
        HarnessConfig.reset(); // so reports and later lookups see the resolved URL
        return new GateSut(url, key, db, ctx);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String testSupportKey() {
        return testSupportKey;
    }

    /** The SUT's database, for grey-box checks. Present in embedded mode. */
    public Optional<PostgresFixture> database() {
        return Optional.ofNullable(database);
    }

    public boolean embedded() {
        return app != null;
    }

    private static String random(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
