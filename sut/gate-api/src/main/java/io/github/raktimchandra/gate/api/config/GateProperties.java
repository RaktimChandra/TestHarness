package io.github.raktimchandra.gate.api.config;

import io.github.raktimchandra.gate.domain.SeededBug;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings (prefix {@code gate}).
 *
 * @param terminalZone  time zone the terminal operates in; slot times are rendered in it
 * @param slotLength    default slot length
 * @param seededBugs    bugs active at start-up (normally empty)
 * @param raceWindow    pause between the capacity check and the insert while RACE_ON_LAST_SLOT
 *                      is active; widens the race so it reproduces on any machine
 * @param seed          demo data created at start-up
 * @param jwt           token settings
 * @param testSupport   time-travel and bug-switch endpoints used by the test suites
 */
@ConfigurationProperties("gate")
public record GateProperties(
        @DefaultValue("Asia/Kolkata") ZoneId terminalZone,
        @DefaultValue("30m") Duration slotLength,
        @DefaultValue List<SeededBug> seededBugs,
        @DefaultValue("150ms") Duration raceWindow,
        @DefaultValue Seed seed,
        @DefaultValue Jwt jwt,
        @DefaultValue TestSupport testSupport) {

    /**
     * @param enabled   create demo users and slots at start-up
     * @param password  password for every seeded and test-created user (override via GATE_SEED_PASSWORD)
     * @param days      how many days ahead to create slots for
     * @param capacity  places per seeded slot
     */
    public record Seed(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("gate-demo-pass") String password,
            @DefaultValue("7") int days,
            @DefaultValue("4") int capacity) {}

    /**
     * @param secret  HMAC secret (at least 32 bytes). When blank a random key is generated per start-up.
     * @param ttl     token lifetime
     */
    public record Jwt(@DefaultValue("") String secret, @DefaultValue("8h") Duration ttl) {}

    /**
     * @param enabled  expose /api/test-support (never in production)
     * @param key      shared secret required in the X-Test-Support-Key header
     */
    public record TestSupport(@DefaultValue("false") boolean enabled, @DefaultValue("") String key) {}
}
