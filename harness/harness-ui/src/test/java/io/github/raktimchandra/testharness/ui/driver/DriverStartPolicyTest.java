package io.github.raktimchandra.testharness.ui.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.raktimchandra.testharness.core.retry.Backoff;
import io.github.raktimchandra.testharness.core.retry.RetryExhaustedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

/** Browser start-up policy: bounded retry on SessionNotCreated only, and a serialised first start. */
class DriverStartPolicyTest {

    private static UiSettings settings(Browser browser, int attempts) {
        return new UiSettings(browser, true, 800, 600, Duration.ofSeconds(1), Duration.ofMillis(50),
                Duration.ofSeconds(5), Optional.empty(), Optional.empty(), Optional.empty(), false, attempts);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        Drivers.resetStartHistory();
    }

    @Test
    void sessionNotCreatedIsRetriedUpToTheLimit() {
        AtomicInteger calls = new AtomicInteger();
        WebDriver ok = mock(WebDriver.class);
        Function<UiSettings, WebDriver> flaky = s -> {
            if (calls.incrementAndGet() == 1) {
                throw new SessionNotCreatedException("browser did not start");
            }
            return ok;
        };
        assertThat(Drivers.create(settings(Browser.EDGE, 2), flaky, Backoff.none())).isSameAs(ok);
        assertThat(calls).hasValue(2);
    }

    @Test
    void retriesAreBoundedAndReportEveryAttempt() {
        AtomicInteger calls = new AtomicInteger();
        Function<UiSettings, WebDriver> broken = s -> {
            calls.incrementAndGet();
            throw new SessionNotCreatedException("never starts");
        };
        assertThatThrownBy(() -> Drivers.create(settings(Browser.EDGE, 2), broken, Backoff.none()))
                .isInstanceOf(RetryExhaustedException.class)
                .hasRootCauseInstanceOf(SessionNotCreatedException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void otherWebDriverErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        Function<UiSettings, WebDriver> wrongConfig = s -> {
            calls.incrementAndGet();
            throw new WebDriverException("unknown capability");
        };
        assertThatThrownBy(() -> Drivers.create(settings(Browser.CHROME, 3), wrongConfig, Backoff.none()))
                .isInstanceOf(WebDriverException.class).hasMessageContaining("unknown capability");
        assertThat(calls).hasValue(1);
    }

    @Test
    void theFirstStartPerBrowserIsAloneThenStartsRunConcurrently() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger peakDuringFirst = new AtomicInteger();
        AtomicInteger peakAfterFirst = new AtomicInteger();
        CountDownLatch never = new CountDownLatch(1);
        Function<UiSettings, WebDriver> slowStart = s -> {
            boolean first = started.incrementAndGet() == 1;
            int now = running.incrementAndGet();
            (first ? peakDuringFirst : peakAfterFirst).accumulateAndGet(now, Math::max);
            try {
                // A timed wait on a latch nobody releases: keeps this start "in progress".
                assertThat(never.await(first ? 400 : 200, TimeUnit.MILLISECONDS)).isFalse();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (first) {
                // Every other worker has had 400 ms to arrive; none may be starting yet.
                peakDuringFirst.accumulateAndGet(running.get(), Math::max);
            }
            running.decrementAndGet();
            return mock(WebDriver.class);
        };
        UiSettings edge = settings(Browser.EDGE, 1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<WebDriver>> all = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                all.add(pool.submit(() -> Drivers.create(edge, slowStart, Backoff.none())));
            }
            for (Future<WebDriver> f : all) {
                assertThat(f.get(10, TimeUnit.SECONDS)).isNotNull();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(started).hasValue(4);
        assertThat(peakDuringFirst).as("browsers starting while the first one starts").hasValue(1);
        assertThat(peakAfterFirst).as("later starts overlap").hasValueGreaterThan(1);
    }
}
