package io.github.raktimchandra.testharness.insights.tia;

import java.util.Optional;

/**
 * Access to the JaCoCo agent's runtime API ({@code org.jacoco.agent.rt.IAgent}) through
 * reflection, so nothing breaks when tests run without the agent.
 */
final class JacocoAgent {

    private final Object agent;
    private final Class<?> api;

    private JacocoAgent(Object agent, Class<?> api) {
        this.agent = agent;
        this.api = api;
    }

    static Optional<JacocoAgent> attached() {
        try {
            ClassLoader system = ClassLoader.getSystemClassLoader();
            Class<?> rt = Class.forName("org.jacoco.agent.rt.RT", true, system);
            Class<?> api = Class.forName("org.jacoco.agent.rt.IAgent", true, system);
            return Optional.of(new JacocoAgent(rt.getMethod("getAgent").invoke(null), api));
        } catch (ReflectiveOperationException | LinkageError e) {
            return Optional.empty();
        }
    }

    void reset() {
        try {
            api.getMethod("reset").invoke(agent);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("JaCoCo agent reset failed", e);
        }
    }

    /** Execution data collected since the last reset, in JaCoCo's .exec format; resets afterwards. */
    byte[] dumpAndReset() {
        try {
            return (byte[]) api.getMethod("getExecutionData", boolean.class).invoke(agent, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("JaCoCo agent dump failed", e);
        }
    }
}
