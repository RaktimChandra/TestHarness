package io.github.raktimchandra.testharness.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScenarioContextTest {

    private static final ScenarioContext.Key<String> REF = ScenarioContext.Key.of("booking reference", String.class);
    private static final ScenarioContext.Key<Integer> COUNT = ScenarioContext.Key.of("count", Integer.class);

    @Test
    void storesTypedValues() {
        ScenarioContext ctx = new ScenarioContext();
        ctx.put(REF, "GA-1234ABCD");
        assertThat(ctx.get(REF)).isEqualTo("GA-1234ABCD");
        assertThat(ctx.has(REF)).isTrue();
        assertThat(ctx.getOrDefault(COUNT, 3)).isEqualTo(3);
    }

    @Test
    void missingValuesPointAtTheMissingStep() {
        assertThatThrownBy(() -> new ScenarioContext().get(REF))
                .hasMessageContaining("no 'booking reference' yet — is a Given step missing?");
    }

    @Test
    void rejectsNullAndWrongTypes() {
        ScenarioContext ctx = new ScenarioContext();
        assertThatThrownBy(() -> ctx.put(REF, null)).hasMessageContaining("null value for booking reference");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ScenarioContext.Key<Object> raw = (ScenarioContext.Key) COUNT;
        assertThatThrownBy(() -> ctx.put(raw, "three")).isInstanceOf(ClassCastException.class);
        assertThat(REF).hasToString("booking reference");
    }
}
