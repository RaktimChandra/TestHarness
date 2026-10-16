package io.github.raktimchandra.testharness.portsync.live;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/** Runs the PortSync feature files (features/portsync) against the running stack. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/portsync")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "io.github.raktimchandra.testharness.portsync.live.steps,io.github.raktimchandra.testharness.bdd.hooks")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,summary,io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm,"
                + "json:target/cucumber/portsync.json,html:target/cucumber/portsync.html")
public class RunPortSyncFeatures {}
