package io.github.raktimchandra.testharness.insights.history;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.TestKeys;
import io.github.raktimchandra.testharness.insights.classify.FailureClassifier;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records every test result of a run (registered through {@code ServiceLoader}, so every suite
 * with this module on its test classpath records automatically).
 *
 * <p>Results always go to a JSON Lines file ({@code history.dir}, default
 * {@code target/test-history}); if {@code history.jdbc-url} is set they are also written to
 * PostgreSQL. A history problem is logged, never allowed to fail the build. Disable with
 * {@code history.enabled=false}.
 */
public class HistoryListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(HistoryListener.class);
    private static final int MAX_MESSAGE = 2000;

    private final Map<String, Long> started = new ConcurrentHashMap<>();
    private final Map<String, Instant> startedAt = new ConcurrentHashMap<>();
    private final List<TestResult> results = Collections.synchronizedList(new ArrayList<>());
    private volatile RunContext context;
    private volatile boolean enabled;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        HarnessConfig config = HarnessConfig.get();
        enabled = config.bool("history.enabled", true);
        if (enabled) {
            context = RunContext.detect(config);
        }
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (enabled && id.isTest()) {
            started.put(id.getUniqueId(), System.nanoTime());
            startedAt.put(id.getUniqueId(), Instant.now());
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (enabled && id.isTest()) {
            record(id, TestResult.Status.SKIPPED, null, 0);
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (!enabled || !id.isTest()) {
            return;
        }
        Long t0 = started.remove(id.getUniqueId());
        long ms = t0 == null ? 0 : (System.nanoTime() - t0) / 1_000_000;
        TestResult.Status status = switch (result.getStatus()) {
            case SUCCESSFUL -> TestResult.Status.PASSED;
            case ABORTED -> TestResult.Status.SKIPPED;
            case FAILED -> TestResult.Status.FAILED;
        };
        record(id, status, status == TestResult.Status.FAILED ? result.getThrowable().orElse(null) : null, ms);
    }

    private void record(TestIdentifier id, TestResult.Status status, Throwable failure, long ms) {
        String key = TestKeys.of(id.getSource()).orElse(id.getUniqueId());
        Instant at = startedAt.getOrDefault(id.getUniqueId(), Instant.now());
        String message = failure == null || failure.getMessage() == null ? null : truncate(failure.getMessage());
        results.add(new TestResult(context.runId(), context.commitSha(), context.branch(), context.suite(), key,
                id.getDisplayName(), status, ms,
                failure == null ? null : FailureClassifier.classify(failure).name(),
                failure == null ? null : failure.getClass().getName(), message, context.environment(), at));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (!enabled || results.isEmpty()) {
            return;
        }
        HarnessConfig config = HarnessConfig.get();
        List<TestResult> snapshot;
        synchronized (results) {
            snapshot = List.copyOf(results);
        }
        Path file = Path.of(config.string("history.dir", "target/test-history"))
                .resolve(context.suite() + "-" + context.runId() + ".jsonl");
        try {
            HistoryJson.append(file, snapshot);
            log.info("Test history: {} results written to {}", snapshot.size(), file);
        } catch (RuntimeException e) {
            log.warn("Test history not written: {}", e.getMessage());
        }
        config.find("history.jdbc-url").ifPresent(url -> {
            try (HistoryStore store = HistoryStore.open(url, config.string("history.db-user", "harness"),
                    config.secret("HARNESS_HISTORY_DB_PASSWORD", "harness").reveal())) {
                log.info("Test history: {} new results stored in PostgreSQL", store.insert(snapshot));
            } catch (RuntimeException e) {
                log.warn("Test history not stored in PostgreSQL: {}", e.getMessage());
            }
        });
    }

    private static String truncate(String s) {
        return s.length() <= MAX_MESSAGE ? s : s.substring(0, MAX_MESSAGE) + "…";
    }
}
