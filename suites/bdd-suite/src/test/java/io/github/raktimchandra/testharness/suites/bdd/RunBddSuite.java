package io.github.raktimchandra.testharness.suites.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs every feature under {@code features/}. Filter by tag with
 * {@code -Dcucumber.filter.tags="@smoke and not @ui"}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "io.github.raktimchandra.testharness.suites.bdd.steps,io.github.raktimchandra.testharness.bdd.hooks")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,summary,io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm,"
                + "json:target/cucumber/cucumber.json,html:target/cucumber/report.html")
public class RunBddSuite {}
