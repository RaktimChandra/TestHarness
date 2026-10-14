package io.github.raktimchandra.testharness.data.shipping;

import java.util.Map;
import java.util.Random;

/**
 * Test-side ISO 6346 oracle and generator.
 *
 * <p>This is written independently of the SUT's implementation on purpose — it uses the
 * standard's letter table verbatim instead of deriving it — so a defect in the SUT's check
 * digit logic cannot be mirrored by the tests that are supposed to catch it.
 */
public final class ContainerNumbers {

    /** ISO 6346 Annex A equivalent values (multiples of 11 are skipped). */
    private static final Map<Character, Integer> LETTER_VALUE = Map.ofEntries(
            Map.entry('A', 10), Map.entry('B', 12), Map.entry('C', 13), Map.entry('D', 14), Map.entry('E', 15),
            Map.entry('F', 16), Map.entry('G', 17), Map.entry('H', 18), Map.entry('I', 19), Map.entry('J', 20),
            Map.entry('K', 21), Map.entry('L', 23), Map.entry('M', 24), Map.entry('N', 25), Map.entry('O', 26),
            Map.entry('P', 27), Map.entry('Q', 28), Map.entry('R', 29), Map.entry('S', 30), Map.entry('T', 31),
            Map.entry('U', 32), Map.entry('V', 34), Map.entry('W', 35), Map.entry('X', 36), Map.entry('Y', 37),
            Map.entry('Z', 38));
    private static final char[] CATEGORIES = {'U', 'J', 'Z'};

    private ContainerNumbers() {}

    /** Weighted sum mod 11 of the first ten characters (before the 10 -> 0 mapping). */
    public static int remainder(String first10) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = first10.charAt(i);
            int value = Character.isDigit(c) ? c - '0' : LETTER_VALUE.get(c);
            sum += value * (1 << i);
        }
        return sum % 11;
    }

    public static int checkDigit(String first10) {
        return remainder(first10) % 10;
    }

    public static boolean isValid(String number) {
        if (number == null || !number.matches("[A-Z]{3}[UJZ][0-9]{7}")) {
            return false;
        }
        return checkDigit(number) == number.charAt(10) - '0';
    }

    /** A valid number with a random owner code; the category is U (freight container). */
    public static String random(Random random) {
        return complete(randomOwner(random) + 'U' + serial(random));
    }

    /** A valid number in any equipment category (U, J or Z). */
    public static String randomAnyCategory(Random random) {
        return complete(randomOwner(random) + CATEGORIES[random.nextInt(3)] + serial(random));
    }

    /** A valid number whose weighted-sum remainder is 10 (check digit 0): the classic edge case. */
    public static String withRemainderTen(Random random) {
        for (int attempt = 0; attempt < 10_000; attempt++) {
            String prefix = randomOwner(random) + 'U' + serial(random);
            if (remainder(prefix) == 10) {
                return prefix + '0';
            }
        }
        throw new IllegalStateException("no remainder-10 number found");
    }

    /** The same number with a wrong check digit. */
    public static String withWrongCheckDigit(String valid) {
        int correct = valid.charAt(10) - '0';
        return valid.substring(0, 10) + (char) ('0' + (correct + 1) % 10);
    }

    private static String complete(String first10) {
        return first10 + checkDigit(first10);
    }

    private static String randomOwner(Random random) {
        StringBuilder sb = new StringBuilder(3);
        for (int i = 0; i < 3; i++) {
            sb.append((char) ('A' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private static String serial(Random random) {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
