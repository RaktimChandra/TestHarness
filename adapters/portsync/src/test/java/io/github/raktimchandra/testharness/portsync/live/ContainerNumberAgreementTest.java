package io.github.raktimchandra.testharness.portsync.live;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import io.github.raktimchandra.testharness.portsync.Models.ContainerValidation;
import io.github.raktimchandra.testharness.reporting.Report;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Differential testing: TestHarness's ISO 6346 oracle and PortSync's validator were written
 * independently. Both are asked about the same numbers; any disagreement is a bug in one of them.
 */
@DisplayName("PortSync: ISO 6346 validation agrees with an independent oracle")
class ContainerNumberAgreementTest extends LiveTest {

    private static final long SEED = 6346L;

    @Test
    @DisplayName("300 generated numbers: valid, remainder-ten, wrong check digit, every category")
    void portSyncAndTheOracleAgreeOnEveryNumber() {
        Random random = new Random(SEED);
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String valid = ContainerNumbers.randomAnyCategory(random);
            numbers.add(valid);
            numbers.add(ContainerNumbers.withWrongCheckDigit(valid));
        }
        for (int i = 0; i < 50; i++) {
            String ten = ContainerNumbers.withRemainderTen(random);
            numbers.add(ten);
            numbers.add(ContainerNumbers.withWrongCheckDigit(ten));
        }
        List<String> disagreements = new ArrayList<>();
        for (String n : numbers) {
            ContainerValidation v = core.validate(n);
            boolean oracle = ContainerNumbers.isValid(n);
            Integer expectedDigit = oracle ? null : ContainerNumbers.checkDigit(n.substring(0, 10));
            if (v.valid() != oracle || (!oracle && !expectedDigit.equals(v.expectedCheckDigit()))) {
                disagreements.add(n + ": PortSync valid=" + v.valid() + " expected digit " + v.expectedCheckDigit()
                        + "; oracle valid=" + oracle + " expected digit " + expectedDigit);
            }
        }
        Report.text("Numbers compared (seed " + SEED + ")", String.join("\n", numbers));
        assertThat(numbers).hasSize(300);
        assertThat(disagreements).as("numbers where PortSync and the oracle disagree").isEmpty();
    }

    @Test
    @DisplayName("Operator input is normalised: lower case and spaces are accepted")
    void lowerCaseAndSpacesAreNormalised() {
        String valid = ContainerNumbers.random(new Random(SEED + 1));
        String typed = " " + valid.substring(0, 4).toLowerCase() + " " + valid.substring(4) + " ";
        ContainerValidation v = core.validate(typed);
        assertThat(v.valid()).as("validation of '%s'", typed).isTrue();
        assertThat(v.input()).isEqualTo(valid);
    }

    @Test
    @DisplayName("Lookup: a wrong check digit is a 400 that names the right digit")
    void lookupOfAWrongCheckDigitSaysWhatItShouldBe() {
        String valid = ContainerNumbers.random(new Random(SEED + 2));
        String wrong = ContainerNumbers.withWrongCheckDigit(valid);
        Response r = core.containerResponse(wrong);
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.jsonPath().getString("code")).isEqualTo("INVALID_CONTAINER_NUMBER");
        assertThat(r.jsonPath().getInt("details.expectedCheckDigit")).isEqualTo(valid.charAt(10) - '0');
    }

    @Test
    @DisplayName("Lookup: a valid number never seen at the terminal is a 404, not a 400")
    void lookupOfAValidUnknownNumberIsNotFound() {
        Response r = core.containerResponse(neverSeenNumber(new Random(SEED + 3)));
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.jsonPath().getString("code")).isEqualTo("CONTAINER_NOT_FOUND");
    }
}
