package io.github.raktimchandra.gate.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.gate.api.config.GateProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    /** A clock the test moves by hand. */
    static final class ManualClock extends Clock {
        Instant now = Instant.parse("2026-10-16T08:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock();

    private LoginThrottle throttle(int perUser, int perClient) {
        GateProperties props = new GateProperties(ZoneId.of("Asia/Kolkata"), Duration.ofMinutes(30), List.of(),
                Duration.ofMillis(150), new GateProperties.Seed(false, "x", 1, 1), new GateProperties.Jwt("", Duration.ofHours(8)),
                new GateProperties.TestSupport(false, ""),
                new GateProperties.LoginThrottle(perUser, perClient, Duration.ofMinutes(15), Duration.ofMinutes(5)));
        return new LoginThrottle(props, clock);
    }

    private static void fail(LoginThrottle t, String user, String client, int times) {
        for (int i = 0; i < times; i++) {
            t.failed(user, client);
        }
    }

    @Test
    void theFifthFailureLocksTheUsernameForTheLockoutPeriod() {
        LoginThrottle t = throttle(5, 100);
        fail(t, "driver7", "10.0.0.1", 4);
        assertThat(t.check("driver7", "10.0.0.1")).isEmpty();
        t.failed("driver7", "10.0.0.1");
        assertThat(t.check("driver7", "10.0.0.1")).hasValueSatisfying(lock -> {
            assertThat(lock.scope()).isEqualTo("user");
            assertThat(lock.retryAfter()).isEqualTo(Duration.ofMinutes(5));
        });
        // from another address too: the lock is on the account
        assertThat(t.check("driver7", "10.0.0.2")).isPresent();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(t.check("driver7", "10.0.0.1")).get().extracting(LoginThrottle.Lock::retryAfter)
                .isEqualTo(Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        assertThat(t.check("driver7", "10.0.0.1")).isEmpty();
    }

    @Test
    void failuresOutsideTheWindowDoNotCount() {
        LoginThrottle t = throttle(5, 100);
        fail(t, "driver7", "10.0.0.1", 4);
        clock.advance(Duration.ofMinutes(15));
        t.failed("driver7", "10.0.0.1");
        assertThat(t.check("driver7", "10.0.0.1")).as("4 failures expired, 1 new").isEmpty();
        fail(t, "driver7", "10.0.0.1", 3);
        assertThat(t.check("driver7", "10.0.0.1")).as("4 within the window").isEmpty();
        t.failed("driver7", "10.0.0.1");
        assertThat(t.check("driver7", "10.0.0.1")).isPresent();
    }

    @Test
    void usernamesAreCountedCaseAndWhitespaceInsensitively() {
        LoginThrottle t = throttle(3, 100);
        t.failed("Driver7", "a");
        t.failed(" driver7 ", "a");
        t.failed("DRIVER7", "a");
        assertThat(t.check("driver7", "b")).isPresent();
    }

    @Test
    void successClearsTheUsernameButNotTheClient() {
        LoginThrottle t = throttle(3, 4);
        fail(t, "driver7", "10.0.0.1", 2);
        t.succeeded("driver7");
        fail(t, "driver7", "10.0.0.1", 2);
        assertThat(t.check("driver7", "10.0.0.9")).as("user count restarted after success").isEmpty();
        // the client has 4 failures: an attacker cannot reset it by signing in to their own account
        assertThat(t.check("someone-else", "10.0.0.1")).hasValueSatisfying(l -> assertThat(l.scope()).isEqualTo("client"));
    }

    @Test
    void oneClientTryingManyUsernamesIsLockedOut() {
        LoginThrottle t = throttle(5, 10);
        for (int i = 0; i < 10; i++) {
            t.failed("user" + i, "203.0.113.5");
        }
        assertThat(t.check("user-never-tried", "203.0.113.5")).isPresent();
        assertThat(t.check("user-never-tried", "198.51.100.1")).as("other clients unaffected").isEmpty();
    }

    @Test
    void unknownUsernamesLockExactlyLikeRealOnes() {
        LoginThrottle t = throttle(2, 100);
        fail(t, "no-such-user", "c", 2);
        fail(t, "driver1", "d", 2);
        assertThat(t.check("no-such-user", "x")).get().usingRecursiveComparison()
                .isEqualTo(t.check("driver1", "y").orElseThrow());
    }

    @Test
    void lockedAttemptsAreNotRecordedByCheckAndNullsAreSafe() {
        LoginThrottle t = throttle(1, 100);
        t.failed(null, null);
        assertThat(t.check(null, null)).isPresent();
        assertThat(t.check("", "")).isPresent();
    }

    @Test
    void manyKeysAreCleanedUpOnceIdle() {
        LoginThrottle t = throttle(1000, 1_000_000);
        for (int i = 0; i <= 10_001; i++) {
            t.failed("u" + i, "c");
        }
        clock.advance(Duration.ofMinutes(16));
        t.failed("late", "c2");
        // all old keys were idle and dropped; the newest are still counted
        assertThat(t.check("late", "c2")).isEmpty();
        fail(t, "late", "c2", 998);
        assertThat(t.check("late", "c2")).isEmpty();
        t.failed("late", "c2");
        assertThat(t.check("late", "c2")).isPresent();
    }

    @Test
    void retryAfterIsRoundedUpToWholeSecondsAndWordedInMinutes() {
        LoginThrottledException e = new LoginThrottledException(Duration.ofMillis(1500));
        assertThat(e.retryAfterSeconds()).isEqualTo(2);
        assertThat(e.getMessage()).isEqualTo("Too many failed sign-in attempts. Try again in a minute.");
        assertThat(new LoginThrottledException(Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
        assertThat(new LoginThrottledException(Duration.ofSeconds(241)).getMessage()).endsWith("in 5 minutes.");
    }
}
