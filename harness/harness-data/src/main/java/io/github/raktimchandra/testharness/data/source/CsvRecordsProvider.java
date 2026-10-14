package io.github.raktimchandra.testharness.data.source;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

public class CsvRecordsProvider implements ArgumentsProvider, AnnotationConsumer<CsvRecords> {

    private static final CsvMapper CSV = new CsvMapper();
    private CsvRecords annotation;

    @Override
    public void accept(CsvRecords a) {
        this.annotation = a;
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return read(annotation.value(), annotation.type()).stream().map(Arguments::of);
    }

    static <T> List<T> read(String resource, Class<T> type) {
        try (InputStream in = CsvRecordsProvider.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Data file not found on classpath: " + resource);
            }
            CsvSchema schema = CsvSchema.emptySchema().withHeader().withComments();
            try (MappingIterator<Map<String, String>> rows = CSV.readerFor(Map.class).with(schema).readValues(in)) {
                List<T> out = rows.readAll().stream()
                        .map(row -> JsonCasesProvider.MAPPER.convertValue(blankToNull(row), type))
                        .toList();
                if (out.isEmpty()) {
                    throw new IllegalArgumentException(resource + " contains no rows");
                }
                return out;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    private static Map<String, String> blankToNull(Map<String, String> row) {
        row.replaceAll((k, v) -> v == null || v.isEmpty() ? null : v);
        return row;
    }
}
