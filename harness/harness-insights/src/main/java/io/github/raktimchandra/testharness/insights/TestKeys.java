package io.github.raktimchandra.testharness.insights;

import java.util.Optional;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;

/**
 * One stable, engine-independent name per test, shared by history, quarantine, selection,
 * re-runs and test impact analysis.
 *
 * <ul>
 *   <li>JUnit Jupiter: {@code com.example.FooTest#bar} (all invocations of a parameterised
 *       method share the key; the display name tells them apart in history)</li>
 *   <li>Cucumber: {@code features/booking_rules.feature:12} (the scenario or example line)</li>
 * </ul>
 */
public final class TestKeys {

    private TestKeys() {}

    public static Optional<String> of(Optional<TestSource> source) {
        return source.flatMap(TestKeys::of);
    }

    public static Optional<String> of(TestSource source) {
        if (source instanceof MethodSource m) {
            return Optional.of(m.getClassName() + "#" + m.getMethodName());
        }
        if (source instanceof ClassSource c) {
            return Optional.of(c.getClassName());
        }
        if (source instanceof ClasspathResourceSource r) {
            String path = r.getClasspathResourceName().replaceFirst("^/", "");
            return Optional.of(path + r.getPosition().map(TestKeys::line).orElse(""));
        }
        if (source instanceof FileSource f) {
            String path = f.getFile().getPath().replace('\\', '/');
            int i = path.indexOf("/features/");
            String shortPath = i >= 0 ? path.substring(i + 1) : path;
            return Optional.of(shortPath + f.getPosition().map(TestKeys::line).orElse(""));
        }
        return Optional.empty();
    }

    /** The class (Jupiter) or feature file (Cucumber) part of a key. */
    public static String owner(String key) {
        int hash = key.indexOf('#');
        if (hash >= 0) {
            return key.substring(0, hash);
        }
        int colon = key.lastIndexOf(':');
        return colon > 0 && key.substring(colon + 1).chars().allMatch(Character::isDigit) ? key.substring(0, colon) : key;
    }

    private static String line(FilePosition p) {
        return ":" + p.getLine();
    }
}
