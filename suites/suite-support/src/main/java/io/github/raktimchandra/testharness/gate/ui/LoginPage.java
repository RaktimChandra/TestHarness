package io.github.raktimchandra.testharness.gate.ui;

import static io.github.raktimchandra.testharness.ui.page.Locators.testId;

import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.heal.Target;
import io.github.raktimchandra.testharness.ui.page.BasePage;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginPage extends BasePage<LoginPage> {

    static final By USERNAME = testId("login-username");
    static final Target USERNAME_FIELD = Target.named("Username field").testId("login-username")
            .accessibleName("input", "Username");
    static final Target PASSWORD_FIELD = Target.named("Password field").testId("login-password")
            .accessibleName("input", "Password");
    static final Target SUBMIT = Target.named("Sign in button").testId("login-submit")
            .accessibleName("button", "Sign in").text("button", "Sign in");
    static final By ERROR = testId("login-error");

    LoginPage(WebDriver driver, Waits waits, String baseUrl) {
        super(driver, waits, baseUrl);
    }

    @Override
    protected By readyLocator() {
        return USERNAME;
    }

    @Override
    protected String path() {
        return "/login";
    }

    public LoginPage enter(String username, String password) {
        type(USERNAME_FIELD, username);
        type(PASSWORD_FIELD, password);
        return this;
    }

    public boolean submitEnabled() {
        return find(SUBMIT).isEnabled();
    }

    public void submit() {
        click(SUBMIT);
    }

    public DriverPage loginAsDriver(GateUser user) {
        Report.step("Sign in as driver " + user.username(), () -> {
            enter(user.username(), user.credentials().password().reveal()).submit();
        });
        return new DriverPage(driver, waits, baseUrl).waitUntilLoaded();
    }

    public ClerkPage loginAsClerk(GateUser user) {
        Report.step("Sign in as clerk " + user.username(), () -> {
            enter(user.username(), user.credentials().password().reveal()).submit();
        });
        return new ClerkPage(driver, waits, baseUrl).waitUntilLoaded();
    }

    public String error() {
        return text(ERROR);
    }
}
