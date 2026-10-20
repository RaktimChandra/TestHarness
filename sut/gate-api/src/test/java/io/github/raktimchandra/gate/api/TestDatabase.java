package io.github.raktimchandra.gate.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL for the SUT's integration tests, one database per test class.
 *
 * <p>By default each class gets its own Testcontainers PostgreSQL 16. Where Linux containers
 * cannot run — GitHub's Windows runners — {@code -Dgate.it.jdbc-url} names an existing server
 * instead, with {@code {db}} standing for the class's database name
 * ({@code jdbc:postgresql://localhost:5432/gate_it_{db}}); user {@code gate.it.db-user} (default
 * {@code gate}), password from {@code GATE_IT_DB_PASSWORD}. Each class still gets its own,
 * freshly created database, because the classes seed different passwords.
 */
final class TestDatabase {

    private static final Map<String, PostgreSQLContainer<?>> CONTAINERS = new ConcurrentHashMap<>();

    private TestDatabase() {}

    static void register(DynamicPropertyRegistry registry, String name) {
        String external = System.getProperty("gate.it.jdbc-url", "");
        if (!external.isBlank()) {
            registry.add("spring.datasource.url", () -> external.replace("{db}", name));
            registry.add("spring.datasource.username", () -> System.getProperty("gate.it.db-user", "gate"));
            registry.add("spring.datasource.password", () -> {
                String password = System.getenv("GATE_IT_DB_PASSWORD");
                if (password == null || password.isBlank()) {
                    throw new IllegalStateException("gate.it.jdbc-url is set but GATE_IT_DB_PASSWORD is not");
                }
                return password;
            });
            return;
        }
        PostgreSQLContainer<?> db = CONTAINERS.computeIfAbsent(name, n -> {
            PostgreSQLContainer<?> c = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("gate_it_" + n);
            c.start();
            return c;
        });
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
    }
}
