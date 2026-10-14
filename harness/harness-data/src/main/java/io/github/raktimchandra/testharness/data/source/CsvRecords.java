package io.github.raktimchandra.testharness.data.source;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Like JUnit's {@code @CsvFileSource}, but maps each row (by header name) to a typed record,
 * so data files can add columns without breaking every test that reads them.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ArgumentsSource(CsvRecordsProvider.class)
public @interface CsvRecords {
    String value();

    Class<?> type();
}
