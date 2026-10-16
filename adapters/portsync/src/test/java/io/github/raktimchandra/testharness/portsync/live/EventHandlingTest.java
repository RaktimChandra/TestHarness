package io.github.raktimchandra.testharness.portsync.live;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.portsync.Models.Container;
import io.github.raktimchandra.testharness.portsync.Models.DlqItem;
import io.github.raktimchandra.testharness.portsync.Models.EventItem;
import io.github.raktimchandra.testharness.portsync.Models.Injection;
import io.github.raktimchandra.testharness.portsync.Models.Published;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Faults injected through the simulator travel through Kafka to the core. Each test follows the
 * exact event ids the injection reports, so it checks what happened to <em>its</em> events.
 */
@DisplayName("PortSync: bad, stale and duplicate events are handled as documented")
class EventHandlingTest extends LiveTest {

    private static UUID only(Injection i) {
        assertThat(i.events()).as("events published by: %s", i.description()).hasSize(1);
        return UUID.fromString(i.events().get(0).eventId());
    }

    private DlqItem deadLettered(UUID eventId) {
        return await(EVENT_TIMEOUT).until("event " + eventId + " in the dead-letter queue", core::deadLetters,
                        list -> list.stream().anyMatch(d -> eventId.equals(d.eventId())))
                .stream().filter(d -> eventId.equals(d.eventId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("A gate-in with a wrong check digit is dead-lettered as INVALID_CHECK_DIGIT, state untouched")
    void invalidCheckDigitIsDeadLettered() {
        long before = core.overview().dlqEvents();
        Injection i = simulator.invalidContainer();
        DlqItem d = deadLettered(only(i));
        assertThat(d.reason()).isEqualTo("INVALID_CHECK_DIGIT");
        assertThat(d.entityId()).isEqualTo(i.events().get(0).entityId());
        assertThat(core.overview().dlqEvents()).isGreaterThan(before);
        assertThat(core.invariants().consistent()).isTrue();
    }

    @Test
    @DisplayName("A gate-out of a container never in the yard is rejected and shows on that container")
    void gateOutOfAGhostIsRejectedAndVisibleOnTheContainer() {
        Injection i = simulator.impossible("GATE_OUT_NOT_IN_YARD");
        DlqItem d = deadLettered(only(i));
        assertThat(d.reason()).isEqualTo("CONTAINER_NOT_IN_YARD");
        Container ghost = core.container(i.events().get(0).entityId());
        assertThat(ghost.location()).isNull();
        assertThat(ghost.rejections()).extracting(r -> r.reason()).contains("CONTAINER_NOT_IN_YARD");
    }

    @Test
    @DisplayName("An unparseable record is dead-lettered as MALFORMED_EVENT")
    void malformedRecordIsDeadLettered() {
        long before = malformed();
        Injection i = simulator.impossible("MALFORMED_JSON");
        assertThat(i.expectedOutcome()).isEqualTo("REJECTED: MALFORMED_EVENT");
        assertThat(i.events()).as("a malformed record has no event id to report").isEmpty();
        await(EVENT_TIMEOUT).until("one more MALFORMED_EVENT dead letter than the " + before + " before",
                this::malformed, n -> n > before);
    }

    private long malformed() {
        return core.deadLetters().stream().filter(d -> "MALFORMED_EVENT".equals(d.reason())).count();
    }

    @Test
    @DisplayName("A back-dated event is recorded as IGNORED_STALE and changes nothing")
    void backDatedEventIsIgnoredAsStale() {
        Injection i = simulator.outOfOrder();
        UUID id = only(i);
        EventItem e = await(EVENT_TIMEOUT).until("event " + id + " to be processed", core::events,
                        list -> list.stream().anyMatch(x -> id.equals(x.eventId())))
                .stream().filter(x -> id.equals(x.eventId())).findFirst().orElseThrow();
        assertThat(e.outcome()).isEqualTo("IGNORED_STALE");
        assertThat(core.invariants().consistent()).isTrue();
    }

    @Test
    @DisplayName("Re-sent events with their original ids are processed exactly once")
    void duplicatesAreProcessedOnce() {
        Injection i = simulator.duplicates(10);
        Set<UUID> ids = i.events().stream().map(Published::eventId).map(UUID::fromString).collect(Collectors.toSet());
        assertThat(ids).isNotEmpty();
        // A marker sent after the duplicates: once it is dead-lettered the consumers have moved past
        // them (exact for the gate topic, the marker's own; the other topics are consumed in parallel).
        deadLettered(only(simulator.invalidContainer()));
        Map<UUID, Long> seen = core.events().stream().filter(e -> ids.contains(e.eventId()))
                .collect(Collectors.groupingBy(EventItem::eventId, Collectors.counting()));
        assertThat(seen).as("re-sent ids found among the last 1000 processed events").hasSize(ids.size());
        assertThat(seen.values()).as("times each re-sent id appears in /api/events").allMatch(n -> n == 1L);
        assertThat(core.invariants().consistent()).isTrue();
    }
}
