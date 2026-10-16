package io.github.raktimchandra.testharness.portsync.live.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.core.retry.Await;
import io.github.raktimchandra.testharness.data.shipping.ContainerNumbers;
import io.github.raktimchandra.testharness.portsync.CoreApi;
import io.github.raktimchandra.testharness.portsync.Models.Alert;
import io.github.raktimchandra.testharness.portsync.Models.ContainerValidation;
import io.github.raktimchandra.testharness.portsync.Models.DlqItem;
import io.github.raktimchandra.testharness.portsync.Models.EventItem;
import io.github.raktimchandra.testharness.portsync.Models.Injection;
import io.github.raktimchandra.testharness.portsync.PortSync;
import io.github.raktimchandra.testharness.portsync.PortSyncReady;
import io.github.raktimchandra.testharness.portsync.Simulator;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;

/** Step definitions for features/portsync. One instance per scenario (PicoContainer). */
public class PortSyncSteps {

    private static final Duration EVENTS = Duration.ofSeconds(30);

    private final CoreApi core = CoreApi.get();
    private final Simulator simulator = Simulator.get();
    private ContainerValidation validation;
    private Response response;
    private UUID eventId;
    private String crane;
    private Alert alert;

    @BeforeAll
    public static void portSyncIsReady() {
        PortSyncReady.await(PortSync.get());
    }

    @After
    public void recoverTheCrane() {
        if (crane != null) {
            simulator.recoverCrane(crane);
        }
    }

    private static Await await(Duration timeout) {
        return Await.atMost(timeout).pollEvery(Duration.ofMillis(500));
    }

    @When("I ask PortSync to validate {string}")
    public void validate(String number) {
        validation = core.validate(number);
    }

    @Then("PortSync says the number is {word}")
    public void verdict(String verdict) {
        assertThat(validation.valid()).as("PortSync's verdict on %s", validation.input()).isEqualTo("valid".equals(verdict));
        if (validation.input() != null && validation.input().length() == 11) {
            assertThat(ContainerNumbers.isValid(validation.input())).as("independent oracle agrees")
                    .isEqualTo(validation.valid());
        }
    }

    @Then("the expected check digit is {word}")
    public void expectedDigit(String digit) {
        assertThat(validation.expectedCheckDigit()).isEqualTo("none".equals(digit) ? null : Integer.valueOf(digit));
    }

    @Then("PortSync normalised it to {string}")
    public void normalised(String number) {
        assertThat(validation.input()).isEqualTo(number);
    }

    @When("I look up a valid container number PortSync has never seen")
    public void lookUpUnknown() {
        String prefix = "THX" + "U" + String.format("%06d", new Random().nextInt(1_000_000));
        response = core.containerResponse(prefix + ContainerNumbers.checkDigit(prefix));
    }

    @Then("PortSync answers {int} with code {string}")
    public void answers(int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.jsonPath().getString("code")).isEqualTo(code);
    }

    private void remember(Injection i) {
        assertThat(i.events()).as(i.description()).hasSize(1);
        eventId = UUID.fromString(i.events().get(0).eventId());
    }

    @When("the simulator sends a gate-in for a container with a wrong check digit")
    public void invalidGateIn() {
        remember(simulator.invalidContainer());
    }

    @When("the simulator sends a back-dated event")
    public void backDated() {
        remember(simulator.outOfOrder());
    }

    @Then("PortSync dead-letters that event as {string}")
    public void deadLettered(String reason) {
        DlqItem d = await(EVENTS).until("event " + eventId + " in the DLQ", core::deadLetters,
                l -> l.stream().anyMatch(x -> eventId.equals(x.eventId())))
                .stream().filter(x -> eventId.equals(x.eventId())).findFirst().orElseThrow();
        assertThat(d.reason()).isEqualTo(reason);
    }

    @Then("PortSync records that event as {string}")
    public void recordedAs(String outcome) {
        EventItem e = await(EVENTS).until("event " + eventId + " to be processed", core::events,
                l -> l.stream().anyMatch(x -> eventId.equals(x.eventId())))
                .stream().filter(x -> eventId.equals(x.eventId())).findFirst().orElseThrow();
        assertThat(e.outcome()).isEqualTo(outcome);
    }

    @Then("the yard is still consistent")
    public void consistent() {
        assertThat(core.invariants().violations()).isEmpty();
    }

    @Given("a quay crane breaks down with fault code {string}")
    public void craneBreaksDown(String code) {
        crane = core.cranes().stream().filter(c -> !"FAULTED".equals(c.status())).reduce((a, b) -> b)
                .orElseThrow().id();
        simulator.faultCrane(crane, code);
    }

    @Then("a critical {string} alert opens for that crane")
    public void alertOpens(String rule) {
        Duration timeout = HarnessConfig.get().duration("portsync.alert-timeout", Duration.ofSeconds(90));
        alert = await(timeout).until("an OPEN " + rule + " alert for " + crane, () -> core.alerts("OPEN"),
                        l -> l.stream().anyMatch(a -> rule.equals(a.rule()) && crane.equals(a.entityId())))
                .stream().filter(a -> rule.equals(a.rule()) && crane.equals(a.entityId())).findFirst().orElseThrow();
        assertThat(alert.severity()).isEqualTo("CRITICAL");
    }

    @When("the operator acknowledges the alert")
    public void acknowledge() {
        response = core.acknowledge(alert.id());
    }

    @Then("the alert is {string}")
    public void alertIs(String state) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.as(Alert.class).state()).isEqualTo(state);
    }

    @Then("acknowledging it again is refused with {string}")
    public void acknowledgeAgain(String code) {
        Response again = core.acknowledge(alert.id());
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(again.jsonPath().getString("code")).isEqualTo(code);
    }
}
