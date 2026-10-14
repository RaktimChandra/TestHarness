package io.github.raktimchandra.testharness.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UniqueIdsTest {

    @Test
    void idsAreUniqueUnderConcurrencyAndHaveTheRequestedShape() {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 2000).parallel().forEach(i -> ids.add(UniqueIds.next("T", 8)));
        assertThat(ids).hasSize(2000).allMatch(id -> id.matches("T[0-9A-Z]{7}"));
    }

    @Test
    void usernamesAreLowerCase() {
        assertThat(UniqueIds.username("DRIVER")).matches("driver-[0-9a-z]{3}-[0-9a-z]{4,}");
    }

    @Test
    void rejectsLengthsWithNoRoomForACounter() {
        assertThatThrownBy(() -> UniqueIds.next("LONGPREFIX", 12)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seedIsStableAndFakerWorks() {
        assertThat(TestData.seed()).isEqualTo(TestData.seed());
        assertThat(TestData.faker().name().fullName()).isNotBlank();
    }
}
