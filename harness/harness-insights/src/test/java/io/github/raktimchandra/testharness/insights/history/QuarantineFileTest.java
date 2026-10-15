package io.github.raktimchandra.testharness.insights.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarantineFileTest {

    @TempDir
    Path dir;

    @Test
    void roundTrips() {
        Quarantine q = Quarantine.from(FlakinessTest.runs("Q#q", "c", "PFPFPF", Instant.EPOCH),
                Flakiness.Policy.DEFAULT, Instant.parse("2026-10-15T00:00:00Z"));
        Path f = dir.resolve("quarantine.json");
        q.write(f);
        Quarantine back = Quarantine.read(f);
        assertThat(back.keys()).containsExactly("Q#q");
        assertThat(back.tests().get(0).score()).isEqualTo(1.0);
        assertThat(back.generated()).isEqualTo(q.generated());
    }

    @Test
    void aMissingFileIsAnEmptyQuarantine() {
        assertThat(Quarantine.read(dir.resolve("nope.json")).keys()).isEmpty();
    }
}
