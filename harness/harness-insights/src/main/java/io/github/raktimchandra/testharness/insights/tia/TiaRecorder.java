package io.github.raktimchandra.testharness.insights.tia;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.TestKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records which code each test executes, for test impact analysis. Active only with
 * {@code tia.record=true}; needs the JaCoCo agent (every module's surefire run has it) and
 * <b>sequential</b> execution — with parallel tests, coverage from concurrent tests would be
 * mixed, so the recorder refuses to record rather than produce a wrong map.
 *
 * <p>Writes {@code <tia.dir>/<suite>/<n>.exec} per test and an {@code index.jsonl} naming each.
 * The embedded SUT runs in the test JVM, so server-side code a test reaches over HTTP or
 * through the browser is attributed to that test.
 */
public class TiaRecorder implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(TiaRecorder.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AtomicInteger counter = new AtomicInteger();
    private final Map<String, Long> started = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Optional<JacocoAgent> agent = Optional.empty();
    private volatile Path dir;

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        HarnessConfig config = HarnessConfig.get();
        if (!config.bool("tia.record", false)) {
            return;
        }
        if (plan.getConfigurationParameters().getBoolean("junit.jupiter.execution.parallel.enabled").orElse(false)) {
            log.error("tia.record needs sequential tests; run with -Djunit.jupiter.execution.parallel.enabled=false."
                    + " Not recording.");
            return;
        }
        agent = JacocoAgent.attached();
        if (agent.isEmpty()) {
            log.error("tia.record: JaCoCo agent not attached to this JVM. Not recording.");
            return;
        }
        String suite = config.find("history.suite").orElse(Path.of(System.getProperty("user.dir")).getFileName().toString());
        dir = Path.of(config.string("tia.dir", "target/tia")).resolve(suite);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Recording per-test coverage to {}", dir.toAbsolutePath());
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (agent.isPresent() && id.isTest()) {
            agent.get().reset();
            started.put(id.getUniqueId(), System.nanoTime());
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (agent.isEmpty() || !id.isTest()) {
            return;
        }
        byte[] data = agent.get().dumpAndReset();
        Long t0 = started.remove(id.getUniqueId());
        String file = counter.incrementAndGet() + ".exec";
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("exec", file);
        entry.put("key", TestKeys.of(id.getSource()).orElse(id.getUniqueId()));
        entry.put("displayName", id.getDisplayName());
        entry.put("status", result.getStatus().name());
        entry.put("durationMs", t0 == null ? 0 : (System.nanoTime() - t0) / 1_000_000);
        try {
            Files.write(dir.resolve(file), data);
            Files.writeString(dir.resolve("index.jsonl"), JSON.writeValueAsString(entry) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
