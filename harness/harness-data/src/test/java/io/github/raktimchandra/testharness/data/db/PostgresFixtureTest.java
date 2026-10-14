package io.github.raktimchandra.testharness.data.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Needs Docker: starts a real PostgreSQL container through Testcontainers. */
class PostgresFixtureTest {

    @Test
    void startsOnceSeedsAndQueriesWithBoundParameters() {
        PostgresFixture db = PostgresFixture.shared();
        assertThat(PostgresFixture.shared()).isSameAs(db);
        assertThat(db.isContainer()).isTrue();
        assertThat(db.jdbcUrl()).startsWith("jdbc:postgresql://");
        db.runScript("/db/fixture.sql");
        Jdbc jdbc = db.jdbc();
        assertThat(jdbc.count("SELECT count(*) FROM fixture_lane")).isEqualTo(2);
        assertThat(jdbc.query("SELECT code FROM fixture_lane WHERE code = ?", "L2"))
                .singleElement().satisfies(row -> assertThat(row).containsEntry("code", "L2"));
        // A classic injection payload is just a value when bound.
        assertThat(jdbc.query("SELECT code FROM fixture_lane WHERE code = ?", "L1' OR '1'='1")).isEmpty();
        assertThat(jdbc.update("UPDATE fixture_lane SET open = false WHERE code = ?", "L1")).isEqualTo(1);
    }

    @Test
    void errorsNameTheStatement() {
        Jdbc jdbc = PostgresFixture.shared().jdbc();
        assertThatThrownBy(() -> jdbc.query("SELECT * FROM missing_table")).hasMessageContaining("missing_table");
        assertThatThrownBy(() -> PostgresFixture.shared().runScript("/db/none.sql")).hasMessageContaining("not found");
    }
}
