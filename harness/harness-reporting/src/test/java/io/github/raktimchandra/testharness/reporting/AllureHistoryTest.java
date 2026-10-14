package io.github.raktimchandra.testharness.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllureHistoryTest {

    @TempDir
    Path dir;

    @Test
    void roundTripsHistoryBetweenReportAndNextRun() throws IOException {
        Path report = dir.resolve("report");
        Files.createDirectories(report.resolve("history/nested"));
        Files.writeString(report.resolve("history/history-trend.json"), "[1]");
        Files.writeString(report.resolve("history/nested/x.json"), "{}");
        Path store = dir.resolve("store");
        assertThat(AllureHistory.save(report, store)).isEqualTo(2);
        Path results = dir.resolve("results");
        assertThat(AllureHistory.restore(store, results)).isEqualTo(2);
        assertThat(Files.readString(results.resolve("history/history-trend.json"))).isEqualTo("[1]");
        assertThat(results.resolve("history/nested/x.json")).exists();
    }

    @Test
    void missingHistoryIsNotAnError() {
        assertThat(AllureHistory.restore(dir.resolve("none"), dir.resolve("r"))).isZero();
    }

    @Test
    void mainSavesAndValidatesArguments() throws IOException {
        Path report = dir.resolve("rep");
        Files.createDirectories(report.resolve("history"));
        Files.writeString(report.resolve("history/a.json"), "a");
        AllureHistory.main(new String[] {"save", report.toString(), dir.resolve("s").toString()});
        assertThat(dir.resolve("s/a.json")).exists();
        assertThatThrownBy(() -> AllureHistory.main(new String[] {"load"})).isInstanceOf(IllegalArgumentException.class);
    }
}
