package io.github.raktimchandra.testharness.data.source;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Feeds a parameterized test from a JSON array on the classpath; each element is mapped to
 * {@link #type()} (typically a record) and passed as the single argument.
 *
 * <pre>{@code
 * @ParameterizedTest(name = "{0}")
 * @JsonCases(value = "/data/booking-rules.json", type = BookingCase.class)
 * void rule(BookingCase c) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ArgumentsSource(JsonCasesProvider.class)
public @interface JsonCases {
    String value();

    Class<?> type();
}
