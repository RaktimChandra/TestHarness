package io.github.raktimchandra.testharness.bdd.fixture;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.github.raktimchandra.testharness.core.logging.TestLog;
import io.github.raktimchandra.testharness.ui.driver.Drivers;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Steps for ScenarioHooksTest only (glue package bdd.fixture). */
public class HookFixtureSteps {

    public static final List<Map<String, String>> CONTEXTS = new CopyOnWriteArrayList<>();
    public static final List<Boolean> BROWSER_OPEN = new CopyOnWriteArrayList<>();

    @Given("the log context is recorded")
    public void record() {
        CONTEXTS.add(TestLog.snapshot());
        BROWSER_OPEN.add(Drivers.session().isPresent());
    }

    @Given("a page in the browser")
    public void page() {
        BROWSER_OPEN.add(Drivers.session().isPresent());
        Drivers.current().get("data:text/html,<title>hooks</title><h1>evidence page</h1>");
    }

    @Then("this step fails")
    public void fails() {
        throw new AssertionError("deliberate failure to trigger evidence capture");
    }
}
