package io.github.raktimchandra.testharness.gate.ui;

import static io.github.raktimchandra.testharness.ui.page.Locators.testId;

import io.github.raktimchandra.testharness.gate.api.GateUser;
import io.github.raktimchandra.testharness.reporting.Report;
import io.github.raktimchandra.testharness.ui.page.BasePage;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginPage extends BasePage<LoginPage> {

    static final By USERNAME = testId("login-username");
    static final By PASSWORD = testId("login-password");
    static final By SUBMIT = testId("login-submit");
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
        type(USERNAME, username);
        type(PASSWORD, password);
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
