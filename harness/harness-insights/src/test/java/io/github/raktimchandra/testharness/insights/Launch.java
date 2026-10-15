package io.github.raktimchandra.testharness.insights;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/** Runs fixture classes in a nested launcher with only the listeners/filters a test names. */
public final class Launch {

    private Launch() {}

    public static Launcher launcher(List<TestExecutionListener> listeners) {
        return LauncherFactory.create(LauncherConfig.builder()
                .enableTestExecutionListenerAutoRegistration(false)
                .enablePostDiscoveryFilterAutoRegistration(false)
                .enableLauncherDiscoveryListenerAutoRegistration(false)
                .enableLauncherSessionListenerAutoRegistration(false)
                .addTestExecutionListeners(listeners.toArray(TestExecutionListener[]::new))
                .build());
    }

    public static LauncherDiscoveryRequest request(Class<?> fixture, PostDiscoveryFilter... filters) {
        return LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .filters(filters).build();
    }

    public static void run(Class<?> fixture, TestExecutionListener... listeners) {
        launcher(List.of(listeners)).execute(request(fixture));
    }

    public static TestPlan discover(Class<?> fixture, PostDiscoveryFilter... filters) {
        return launcher(List.of()).discover(request(fixture, filters));
    }
}
