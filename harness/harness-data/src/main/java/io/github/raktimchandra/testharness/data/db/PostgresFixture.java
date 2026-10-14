package io.github.raktimchandra.testharness.data.db;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A PostgreSQL database for tests. In the local and CI environments it is a Testcontainers
 * container started once per JVM; when {@code db.url} is configured (Docker Compose) it
 * connects to that database instead.
 */
public final class PostgresFixture {

    private static final Logger log = LoggerFactory.getLogger(PostgresFixture.class);
    private static volatile PostgresFixture shared;

    private final String url;
    private final String user;
    private final String password;
    private final PostgreSQLContainer<?> container;

    private PostgresFixture(String url, String user, String password, PostgreSQLContainer<?> container) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.container = container;
    }

    /** The JVM-wide database, started on first use. */
    public static PostgresFixture shared() {
        PostgresFixture f = shared;
        if (f == null) {
            synchronized (PostgresFixture.class) {
                f = shared;
                if (f == null) {
                    f = start(HarnessConfig.get());
                    shared = f;
                }
            }
        }
        return f;
    }

    static PostgresFixture start(HarnessConfig config) {
        var external = config.find("db.url");
        if (external.isPresent()) {
            log.info("Using external database {}", external.get());
            return new PostgresFixture(external.get(), config.string("db.user", "gate"),
                    config.secret("HARNESS_DB_PASSWORD", "gate").reveal(), null);
        }
        String image = config.string("db.image", "postgres:16-alpine");
        PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(DockerImageName.parse(image))
                .withDatabaseName("gate").withUsername("gate").withPassword("gate");
        pg.start();
        log.info("Started {} at {}", image, pg.getJdbcUrl());
        return new PostgresFixture(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(), pg);
    }

    public String jdbcUrl() {
        return url;
    }

    public String username() {
        return user;
    }

    public String password() {
        return password;
    }

    public boolean isContainer() {
        return container != null;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public Jdbc jdbc() {
        return new Jdbc(this);
    }

    /** Runs a classpath SQL script (statements separated by semicolons at line ends). */
    public void runScript(String resource) {
        try (InputStream in = PostgresFixture.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("SQL script not found: " + resource);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = connect(); var st = c.createStatement()) {
                for (String statement : sql.split(";\\s*(\\r?\\n|$)")) {
                    if (!statement.isBlank()) {
                        st.execute(statement);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new IllegalStateException("Script " + resource + " failed: " + e.getMessage(), e);
        }
    }
}
