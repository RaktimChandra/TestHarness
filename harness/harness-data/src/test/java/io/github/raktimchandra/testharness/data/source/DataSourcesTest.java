package io.github.raktimchandra.testharness.data.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

class DataSourcesTest {

    record Case(String name, String container, boolean valid, Duration lead) {}

    record Row(String container, Boolean valid, String note) {}

    @ParameterizedTest(name = "{0}")
    @JsonCases(value = "/data/containers.json", type = Case.class)
    void jsonCasesDriveTests(Case c) {
        assertThat(ContainerNumbers.isValid(c.container())).as(c.name()).isEqualTo(c.valid());
        assertThat(c.lead()).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvRecords(value = "/data/containers.csv", type = Row.class)
    void csvRowsMapToRecords(Row r) {
        assertThat(ContainerNumbers.isValid(r.container())).isEqualTo(r.valid());
    }

    @Test
    void blankCsvCellsBecomeNull() {
        assertThat(CsvRecordsProvider.read("/data/containers.csv", Row.class))
                .anySatisfy(r -> assertThat(r.note()).isNull());
    }

    @Test
    void missingOrMalformedFilesFailClearly() {
        assertThatThrownBy(() -> JsonCasesProvider.read("/data/nope.json", Case.class))
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> JsonCasesProvider.read("/data/not-an-array.json", Case.class))
                .hasMessageContaining("must contain a JSON array");
        assertThatThrownBy(() -> JsonCasesProvider.read("/data/empty.json", Case.class))
                .hasMessageContaining("no cases");
        assertThatThrownBy(() -> JsonCasesProvider.read("/data/unknown-field.json", Case.class))
                .hasMessageContaining("Could not read");
        assertThatThrownBy(() -> CsvRecordsProvider.read("/data/nope.csv", Row.class))
                .hasMessageContaining("not found");
    }
}
