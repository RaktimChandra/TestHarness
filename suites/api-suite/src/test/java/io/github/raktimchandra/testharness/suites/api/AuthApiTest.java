package io.github.raktimchandra.testharness.suites.api;

import static io.github.raktimchandra.testharness.api.assertion.ResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.gate.api.GateFixtures;
import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.gate.api.Models;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Auth API")
class AuthApiTest extends ApiTest {

    @Tag("smoke")
    @ParameterizedTest(name = "{0} logs in as {1}")
    @CsvSource({"driver1,DRIVER", "clerk1,CLERK"})
    void seeded_users_log_in_and_receive_a_bearer_token(String username, String role) {
        GateUser user = GateFixtures.seeded(username);
        Response r = api.login(user.username(), user.credentials().password().reveal());
        assertThatResponse(r).hasStatus(200);
        Models.Login login = r.as(Models.Login.class);
        assertThat(login.tokenType()).isEqualTo("Bearer");
        assertThat(login.user().role()).isEqualTo(role);
        assertThat(login.expiresAt()).isAfter(OffsetDateTime.now().plusHours(7));
    }

    @Test
    void wrong_password_and_unknown_user_get_the_same_401() {
        // A fresh account: failed sign-ins count towards a lock, so tests never spend a shared one's.
        Response wrongPassword = api.login(fx.newDriver().username(), "not-the-password");
        Response unknownUser = api.login("nobody-" + System.nanoTime(), "whatever");
        assertThatResponse(wrongPassword).hasStatus(401).hasProblemCode("UNAUTHENTICATED");
        assertThatResponse(unknownUser).hasStatus(401).hasProblemCode("UNAUTHENTICATED");
        // Same message for both: the API does not reveal which usernames exist.
        assertThat(wrongPassword.jsonPath().getString("detail")).isEqualTo(unknownUser.jsonPath().getString("detail"));
    }

    @Test
    void blank_credentials_are_a_validation_error_listing_each_field() {
        Response r = api.anonymous().body(Map.of("username", "", "password", "")).post("/api/auth/login");
        assertThatResponse(r).hasStatus(400).hasProblemCode("VALIDATION_FAILED");
        assertThat(r.jsonPath().getList("errors.field", String.class)).containsExactly("password", "username");
    }

    @Test
    void malformed_json_is_rejected() {
        Response r = api.anonymous().body("{\"username\": ").post("/api/auth/login");
        assertThatResponse(r).hasStatus(400).hasProblemCode("VALIDATION_FAILED");
    }

    @Test
    void me_returns_the_caller() {
        GateUser driver = fx.newDriver();
        Models.User me = api.as(driver).get("/api/auth/me").as(Models.User.class);
        assertThat(me.username()).isEqualTo(driver.username());
        assertThat(me.role()).isEqualTo("DRIVER");
    }

    @Test
    void me_without_a_token_is_401() {
        assertThatResponse(api.anonymous().get("/api/auth/me")).hasStatus(401).hasProblemCode("UNAUTHENTICATED");
    }

    @Test
    void a_tampered_token_is_rejected() {
        String token = api.login("driver1", GateFixtures.seeded("driver1").credentials().password().reveal())
                .as(Models.Login.class).token();
        String[] parts = token.split("\\.");
        // Flip the role claim without re-signing: the signature check must fail.
        String forged = parts[0] + "." + parts[1].replace(parts[1].charAt(5), parts[1].charAt(5) == 'A' ? 'B' : 'A')
                + "." + parts[2];
        Response r = api.anonymous().auth().oauth2(forged).get("/api/auth/me");
        assertThatResponse(r).hasStatus(401).hasProblemCode("UNAUTHENTICATED");
    }

    @Test
    void garbage_bearer_tokens_are_rejected() {
        assertThatResponse(api.anonymous().auth().oauth2("not-a-jwt").get("/api/slots?date=2026-01-01"))
                .hasStatus(401).hasProblemCode("UNAUTHENTICATED");
    }

    @Test
    void wrong_http_method_is_405_with_a_problem_body() {
        Response r = api.as(fx.newDriver()).put("/api/slots");
        assertThatResponse(r).hasStatus(405).hasProblemCode("METHOD_NOT_ALLOWED");
    }

    @Tag("smoke")
    @Test
    void health_endpoint_is_public_and_up() {
        Response r = api.anonymous().get("/actuator/health");
        assertThatResponse(r).hasStatus(200).hasJsonValue("status", "UP");
    }
}
