package io.github.raktimchandra.gate.domain;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Generates human-friendly booking references such as {@code GA-7KQ2M9XD}, using Crockford's
 * base-32 alphabet (no I, L, O or U, so references read aloud at the gate are unambiguous).
 */
public final class BookingReferences {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final String PATTERN = "GA-[0-9A-HJKMNP-TV-Z]{8}";

    private final RandomGenerator random;

    public BookingReferences() {
        this(new SecureRandom());
    }

    public BookingReferences(RandomGenerator random) {
        this.random = random;
    }

    public String next() {
        StringBuilder sb = new StringBuilder("GA-");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
