package io.github.raktimchandra.testharness.data.source;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

public class JsonCasesProvider implements ArgumentsProvider, AnnotationConsumer<JsonCases> {

    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonCases annotation;

    @Override
    public void accept(JsonCases a) {
        this.annotation = a;
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return read(annotation.value(), annotation.type()).stream().map(Arguments::of);
    }

    static <T> List<T> read(String resource, Class<T> type) {
        try (InputStream in = JsonCasesProvider.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Data file not found on classpath: " + resource);
            }
            JsonNode root = MAPPER.readTree(in);
            if (!root.isArray()) {
                throw new IllegalArgumentException(resource + " must contain a JSON array");
            }
            List<T> cases = new ArrayList<>();
            for (JsonNode n : root) {
                cases.add(MAPPER.treeToValue(n, type));
            }
            if (cases.isEmpty()) {
                throw new IllegalArgumentException(resource + " contains no cases");
            }
            return cases;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource + " as " + type.getSimpleName(), e);
        }
    }
}
