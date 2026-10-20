package io.github.raktimchandra.gate.api.service;

import io.github.raktimchandra.gate.api.config.GateProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limits failed sign-ins. Two independent counters, each a sliding window of failure times:
 * per username (stops guessing one account's password) and per client address (stops one
 * client trying a password against many accounts). Reaching a limit locks that key for the
 * lockout period; while locked, every attempt is refused before the password is checked, so a
 * correct password during a lock is refused too and reveals nothing.
 *
 * <p>Unknown usernames are counted and locked exactly like real ones: whether a username exists
 * must not be observable through the lock either. A successful sign-in clears its username's
 * failures but never the client's (an attacker could otherwise reset the client counter with
 * an account of their own). The client is the TCP peer address: forwarded-for headers are not
 * trusted, because the app is not deployed behind a proxy that sets them.
 *
 * <p>Known trade-off: anyone can keep a known username locked by failing five times every
 * lockout period. Per-user locking always allows that; the lock is short for that reason.
 */
@Component
public class LoginThrottle {

    /** A refused attempt: which limit applies ("user" or "client") and how long until it lifts. */
    public record Lock(String scope, Duration retryAfter) {}

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final GateProperties.LoginThrottle limits;
    private final Clock clock;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /**
     * Wall-clock time, deliberately not the application's shiftable clock: moving the test
     * clock (time travel) must not lift or extend a lock.
     */
    @Autowired
    public LoginThrottle(GateProperties props) {
        this(props, Clock.systemUTC());
    }

    LoginThrottle(GateProperties props, Clock clock) {
        this.limits = props.loginThrottle();
        this.clock = clock;
    }

    /** The lock that refuses this attempt, if any. Call before checking the password. */
    public Optional<Lock> check(String username, String client) {
        Instant now = clock.instant();
        Optional<Lock> user = lockOf(userKey(username), "user", now);
        return user.isPresent() ? user : lockOf(clientKey(client), "client", now);
    }

    public void failed(String username, String client) {
        Instant now = clock.instant();
        record(userKey(username), limits.maxFailuresPerUser(), now);
        record(clientKey(client), limits.maxFailuresPerClient(), now);
        if (counters.size() > CLEANUP_THRESHOLD) {
            counters.entrySet().removeIf(e -> e.getValue().idle(now, limits.window()));
        }
    }

    public void succeeded(String username) {
        counters.remove(userKey(username));
    }

    private Optional<Lock> lockOf(String key, String scope, Instant now) {
        Counter c = counters.get(key);
        if (c == null) {
            return Optional.empty();
        }
        synchronized (c) {
            return c.lockedUntil != null && now.isBefore(c.lockedUntil)
                    ? Optional.of(new Lock(scope, Duration.between(now, c.lockedUntil))) : Optional.empty();
        }
    }

    private void record(String key, int max, Instant now) {
        Counter c = counters.computeIfAbsent(key, k -> new Counter());
        synchronized (c) {
            Instant horizon = now.minus(limits.window());
            while (!c.failures.isEmpty() && !c.failures.peekFirst().isAfter(horizon)) {
                c.failures.removeFirst();
            }
            c.failures.addLast(now);
            if (c.failures.size() >= max) {
                c.lockedUntil = now.plus(limits.lockout());
                c.failures.clear();
            }
        }
    }

    private static String userKey(String username) {
        return "u:" + (username == null ? "" : username.strip().toLowerCase(Locale.ROOT));
    }

    private static String clientKey(String client) {
        return "c:" + (client == null ? "" : client);
    }

    private static final class Counter {
        final Deque<Instant> failures = new ArrayDeque<>();
        Instant lockedUntil;

        synchronized boolean idle(Instant now, Duration window) {
            boolean locked = lockedUntil != null && now.isBefore(lockedUntil);
            boolean recent = !failures.isEmpty() && failures.peekLast().isAfter(now.minus(window));
            return !locked && !recent;
        }
    }
}
