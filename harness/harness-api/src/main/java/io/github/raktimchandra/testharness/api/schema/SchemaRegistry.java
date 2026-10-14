package io.github.raktimchandra.testharness.api.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps API operations to JSON Schemas (draft 2020-12, with format assertions on) and
 * validates response bodies against them. Schemas may {@code $ref} each other through
 * {@code classpath:schemas/...} URIs.
 */
public final class SchemaRegistry {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, SchemaRegistry> CACHE = new ConcurrentHashMap<>();

    private final String base;
    private final List<SchemaRule> rules;
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final SchemaValidatorsConfig validatorConfig =
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();

    SchemaRegistry(String base, List<SchemaRule> rules) {
        this.base = base;
        this.rules = rules.stream().sorted(Comparator.comparingInt(SchemaRule::specificity).reversed()).toList();
    }

    /** Loads {@code <base>/registry.yml}, e.g. {@code schemas/registry.yml}; cached per base. */
    public static SchemaRegistry load(String base) {
        return CACHE.computeIfAbsent(base, b -> {
            String resource = "/" + b + "/registry.yml";
            try (InputStream in = SchemaRegistry.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Schema registry not found on classpath: " + resource);
                }
                Map<String, List<SchemaRule>> doc = YAML.readValue(in, new TypeReference<>() {});
                List<SchemaRule> rules = doc.get("rules");
                if (rules == null || rules.isEmpty()) {
                    throw new IllegalStateException(resource + " has no rules");
                }
                return new SchemaRegistry(b, rules);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + resource, e);
            }
        });
    }

    public List<SchemaRule> rules() {
        return rules;
    }

    public Optional<SchemaRule> ruleFor(String method, String path, int status) {
        return rules.stream().filter(r -> r.matches(method, path, status)).findFirst();
    }

    /** Validates {@code body} against the named schema file; returns human-readable errors. */
    public List<String> validate(String schemaFile, String body) {
        JsonNode node;
        try {
            node = JSON.readTree(body == null || body.isBlank() ? "null" : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of("response body is not valid JSON: " + e.getOriginalMessage());
        }
        Set<ValidationMessage> errors = schema(schemaFile).validate(node);
        return errors.stream()
                .map(ValidationMessage::getMessage) // already prefixed with the JSON pointer
                .sorted()
                .toList();
    }

    private JsonSchema schema(String file) {
        return schemas.computeIfAbsent(file,
                f -> factory.getSchema(SchemaLocation.of("classpath:" + base + "/" + f), validatorConfig));
    }
}
