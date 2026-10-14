package io.github.raktimchandra.gate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContainerNumberTest {

    private static final BugSwitches CORRECT = BugSwitches.none();
    private static final BugSwitches BUGGY = BugSwitches.of(SeededBug.CHECK_DIGIT_EDGE_CASE);

    @Nested
    @DisplayName("correct validator")
    class Correct {

        @ParameterizedTest(name = "{0} is valid")
        @ValueSource(strings = {
            "CSQU3054383", // the worked example in ISO 6346
            "MSCU1000070", // weighted-sum remainder 10 -> check digit 0
            "MSCU1000110", // another remainder-10 case
            "MAEU1000002",
            "csqu3054383", // lower case is normalised
            "  CSQU3054383  " // surrounding whitespace is trimmed
        })
        void acceptsValidNumbers(String raw) {
            assertThat(ContainerNumber.isValid(raw, CORRECT)).isTrue();
            assertThat(ContainerNumber.parse(raw, CORRECT).value()).isEqualTo(raw.strip().toUpperCase());
        }

        @ParameterizedTest(name = "{0} is rejected")
        @ValueSource(strings = {
            "CSQU3054384", // wrong check digit
            "CSQX3054383", // category must be U, J or Z
            "CS1U3054383", // owner code must be letters
            "CSQU305438", // too short
            "CSQU30543833", // too long
            "CSQU30543A3" // serial must be digits
        })
        void rejectsInvalidNumbers(String raw) {
            assertThat(ContainerNumber.isValid(raw, CORRECT)).isFalse();
            assertThatThrownBy(() -> ContainerNumber.parse(raw, CORRECT))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo(ErrorCode.INVALID_CONTAINER_NUMBER);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void rejectsNullAndEmpty(String raw) {
            assertThat(ContainerNumber.isValid(raw, CORRECT)).isFalse();
        }

        @ParameterizedTest(name = "category {0} is allowed")
        @ValueSource(chars = {'U', 'J', 'Z'})
        void acceptsAllEquipmentCategories(char category) {
            String prefix = "ABC" + category + "123456";
            String number = prefix + ContainerNumber.checkDigit(prefix);
            assertThat(ContainerNumber.isValid(number, CORRECT)).isTrue();
        }

        @Test
        void explainsTheExpectedCheckDigit() {
            assertThatThrownBy(() -> ContainerNumber.parse("CSQU3054384", CORRECT))
                    .hasMessage("Check digit should be 3 but was 4");
        }
    }

    @Nested
    @DisplayName("check digit computation")
    class CheckDigit {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"CSQU305438,3", "MSCU100007,0", "MAEU100000,2", "csqu305438,3"})
        void computesIsoCheckDigit(String prefix, int expected) {
            assertThat(ContainerNumber.checkDigit(prefix)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"CSQU30543", "CSQ1305438", "CSQX305438"})
        void rejectsMalformedPrefix(String prefix) {
            assertThatThrownBy(() -> ContainerNumber.checkDigit(prefix)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("with CHECK_DIGIT_EDGE_CASE seeded")
    class Seeded {

        @Test
        void rejectsValidRemainderTenNumbers() {
            assertThat(ContainerNumber.isValid("MSCU1000070", BUGGY)).isFalse();
        }

        @Test
        void stillAcceptsOrdinaryNumbers() {
            assertThat(ContainerNumber.isValid("CSQU3054383", BUGGY)).isTrue();
        }
    }

    @Test
    void uncheckedAcceptsAnyElevenCharacters() {
        assertThat(ContainerNumber.unchecked("abcd1234567").value()).isEqualTo("ABCD1234567");
        assertThatThrownBy(() -> ContainerNumber.unchecked("SHORT")).isInstanceOf(DomainException.class);
    }

    @Test
    void toStringIsTheValue() {
        assertThat(new ContainerNumber("CSQU3054383")).hasToString("CSQU3054383");
    }
}
