package io.github.raktimchandra.testharness.suites.bdd.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.cucumber.cucumberexpressions.Expression;
import io.cucumber.cucumberexpressions.ExpressionFactory;
import io.cucumber.cucumberexpressions.ParameterType;
import io.cucumber.cucumberexpressions.ParameterTypeRegistry;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.java.en.And;
import io.cucumber.java.en.But;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleStep;
import io.github.raktimchandra.testharness.suites.bdd.steps.BookingSteps;
import io.github.raktimchandra.testharness.suites.bdd.steps.ParameterTypes;
import io.github.raktimchandra.testharness.suites.bdd.steps.ScreenSteps;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Measures how much of a set of drafted feature files this suite could run as written: every
 * step of every pickle is matched against the suite's real step definitions (Cucumber
 * expressions and parameter types, read from the glue classes), the way Cucumber itself
 * matches them. Used by the AI evaluation (tools/ai_eval.py) to score drafts by step reuse.
 *
 * <p>Unlike a Cucumber dry run, every step is checked: a dry run marks the steps after the
 * first undefined one as skipped, which says nothing about whether they are defined.
 */
public final class DraftStepCoverage {

    public record StepResult(String text, int matches) {}

    public record FileResult(String file, boolean parses, String parseError, int scenarios, int steps, int matched,
                             List<String> unmatched, int ambiguous) {}

    static final List<Class<?>> GLUE = List.of(BookingSteps.class, ScreenSteps.class);

    private final List<Expression> expressions = new ArrayList<>();
    private final List<String> sources = new ArrayList<>();
    private final List<String> parameterNotes = new ArrayList<>();

    public DraftStepCoverage() {
        ParameterTypeRegistry registry = new ParameterTypeRegistry(Locale.ENGLISH);
        for (Method m : ParameterTypes.class.getDeclaredMethods()) {
            io.cucumber.java.ParameterType pt = m.getAnnotation(io.cucumber.java.ParameterType.class);
            if (pt != null) {
                String name = pt.name().isEmpty() ? m.getName() : pt.name();
                registry.defineParameterType(new ParameterType<>(name, pt.value(), String.class, (String s) -> s));
                parameterNotes.add("# {" + name + "} matches " + pt.value());
            }
        }
        ExpressionFactory factory = new ExpressionFactory(registry);
        for (Class<?> glue : GLUE) {
            for (Method m : glue.getDeclaredMethods()) {
                // by type: a method with several @Then carries them in a container annotation
                for (Class<? extends Annotation> type : List.of(Given.class, When.class, Then.class, And.class, But.class)) {
                    for (Annotation a : m.getAnnotationsByType(type)) {
                        expressions.add(factory.createExpression(stepText(a)));
                        sources.add(stepText(a));
                    }
                }
            }
        }
    }

    public int stepDefinitions() {
        return expressions.size();
    }

    /**
     * The step catalogue the AI drafter is given: notes on the custom parameter types, then
     * every step definition's Cucumber expression, sorted (stable, so the committed file can be
     * checked for freshness).
     */
    public List<String> catalogue() {
        List<String> out = new ArrayList<>(parameterNotes.stream().sorted().toList());
        out.add("# {int}, {string}, {word}: Cucumber's built-in parameters; \"(s)\" is optional text");
        out.addAll(sources.stream().distinct().sorted().toList());
        return out;
    }

    private static String stepText(Annotation a) {
        if (a instanceof Given g) {
            return g.value();
        } else if (a instanceof When w) {
            return w.value();
        } else if (a instanceof Then t) {
            return t.value();
        } else if (a instanceof And n) {
            return n.value();
        } else if (a instanceof But b) {
            return b.value();
        }
        return null;
    }

    public int matches(String stepText) {
        int n = 0;
        for (Expression e : expressions) {
            if (e.match(stepText) != null) {
                n++;
            }
        }
        return n;
    }

    public FileResult check(Path feature) throws IOException {
        byte[] bytes = Files.readAllBytes(feature);
        List<Envelope> envelopes = GherkinParser.builder().includeSource(false).build()
                .parse(feature.toString(), bytes).toList();
        String error = envelopes.stream().flatMap(e -> e.getParseError().stream())
                .map(pe -> pe.getMessage()).findFirst().orElse(null);
        List<Pickle> pickles = envelopes.stream().flatMap(e -> e.getPickle().stream()).toList();
        int steps = 0;
        int matched = 0;
        int ambiguous = 0;
        List<String> unmatched = new ArrayList<>();
        for (Pickle p : pickles) {
            for (PickleStep s : p.getSteps()) {
                steps++;
                int m = matches(s.getText());
                if (m > 0) {
                    matched++;
                }
                if (m > 1) {
                    ambiguous++;
                }
                if (m == 0 && !unmatched.contains(s.getText())) {
                    unmatched.add(s.getText());
                }
            }
        }
        return new FileResult(feature.getFileName().toString(), error == null, error, pickles.size(), steps, matched,
                unmatched, ambiguous);
    }

    /** Checks every .feature under {@code dir} and writes {@code out} (JSON). */
    public Map<String, Object> report(Path dir, Path out) throws IOException {
        List<FileResult> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".feature")).sorted().toList()) {
                files.add(check(f));
            }
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("stepDefinitions", stepDefinitions());
        doc.put("files", files);
        doc.put("steps", files.stream().mapToInt(FileResult::steps).sum());
        doc.put("matched", files.stream().mapToInt(FileResult::matched).sum());
        Files.createDirectories(out.toAbsolutePath().getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(out.toFile(), doc);
        return doc;
    }

    static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
