package io.github.raktimchandra.testharness.data.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContainerNumbersTest {

    private final Random random = new Random(7);

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"CSQU305438,3", "MSCU100007,0", "MAEU100000,2"})
    void matchesPublishedExamples(String prefix, int digit) {
        assertThat(ContainerNumbers.checkDigit(prefix)).isEqualTo(digit);
    }

    @Test
    void remainderTenCaseIsDistinguished() {
        assertThat(ContainerNumbers.remainder("MSCU100007")).isEqualTo(10);
        String edge = ContainerNumbers.withRemainderTen(random);
        assertThat(edge).endsWith("0");
        assertThat(ContainerNumbers.remainder(edge.substring(0, 10))).isEqualTo(10);
        assertThat(ContainerNumbers.isValid(edge)).isTrue();
    }

    @RepeatedTest(50)
    void generatedNumbersAreValid() {
        assertThat(ContainerNumbers.isValid(ContainerNumbers.random(random))).isTrue();
        assertThat(ContainerNumbers.randomAnyCategory(random)).matches("[A-Z]{3}[UJZ][0-9]{7}");
    }

    @Test
    void wrongCheckDigitIsInvalid() {
        String valid = ContainerNumbers.random(random);
        String wrong = ContainerNumbers.withWrongCheckDigit(valid);
        assertThat(wrong).hasSize(11).isNotEqualTo(valid);
        assertThat(ContainerNumbers.isValid(wrong)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CSQX3054383", "csqu3054383", "CSQU305438"})
    void malformedIsInvalid(String v) {
        assertThat(ContainerNumbers.isValid(v)).isFalse();
    }
}
