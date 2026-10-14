package io.github.raktimchandra.gate.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An ISO 6346 freight container identification number, e.g. {@code CSQU3054383}:
 * a 3-letter owner code, an equipment category identifier (U, J or Z), a 6-digit serial
 * number and a check digit.
 *
 * <p>The check digit is computed by mapping each of the first ten characters to a value
 * (letters: A=10 upwards, skipping multiples of 11; digits: face value), multiplying the
 * value at position {@code i} by {@code 2^i}, summing, and taking the sum modulo 11. A
 * remainder of 10 is represented by check digit 0.
 */
public record ContainerNumber(String value) {

    private static final Pattern SHAPE = Pattern.compile("[A-Z]{3}[UJZ][0-9]{7}");
    private static final int[] LETTER_VALUES = buildLetterValues();

    public ContainerNumber {
        Objects.requireNonNull(value, "value");
    }

    /** Normalises (trims, upper-cases) and validates; throws on any violation. */
    public static ContainerNumber parse(String raw, BugSwitches bugs) {
        String normalised = normalise(raw);
        String problem = problemWith(normalised, bugs);
        if (problem != null) {
            throw new DomainException(ErrorCode.INVALID_CONTAINER_NUMBER, problem);
        }
        return new ContainerNumber(normalised);
    }

    /** Accepts any 11-character value without checking the format (used by a seeded bug). */
    public static ContainerNumber unchecked(String raw) {
        String normalised = normalise(raw);
        if (normalised.length() != 11) {
            throw new DomainException(ErrorCode.INVALID_CONTAINER_NUMBER, "Container number must be 11 characters");
        }
        return new ContainerNumber(normalised);
    }

    public static boolean isValid(String raw, BugSwitches bugs) {
        return problemWith(normalise(raw), bugs) == null;
    }

    /** Returns a human-readable problem description, or {@code null} when the value is valid. */
    static String problemWith(String value, BugSwitches bugs) {
        if (!SHAPE.matcher(value).matches()) {
            return "Container number must be 3 letters, a category letter (U, J or Z), 6 digits and a check digit";
        }
        int remainder = weightedSum(value) % 11;
        int actual = value.charAt(10) - '0';
        int expected = bugs.isEnabled(SeededBug.CHECK_DIGIT_EDGE_CASE) ? remainder : remainder % 10;
        if (actual != expected) {
            return "Check digit should be " + (remainder % 10) + " but was " + actual;
        }
        return null;
    }

    /** The correct ISO 6346 check digit for the first ten characters of {@code prefix}. */
    public static int checkDigit(String prefix) {
        String p = normalise(prefix);
        if (p.length() < 10 || !SHAPE.matcher(p.substring(0, 10) + "0").matches()) {
            throw new IllegalArgumentException("Need owner code, category and 6-digit serial: " + prefix);
        }
        return (weightedSum(p) % 11) % 10;
    }

    private static int weightedSum(String value) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = value.charAt(i);
            int v = Character.isDigit(c) ? c - '0' : LETTER_VALUES[c - 'A'];
            sum += v << i;
        }
        return sum;
    }

    private static String normalise(String raw) {
        return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
    }

    private static int[] buildLetterValues() {
        int[] values = new int[26];
        int next = 10;
        for (int i = 0; i < 26; i++) {
            if (next % 11 == 0) {
                next++;
            }
            values[i] = next++;
        }
        return values;
    }

    @Override
    public String toString() {
        return value;
    }
}
