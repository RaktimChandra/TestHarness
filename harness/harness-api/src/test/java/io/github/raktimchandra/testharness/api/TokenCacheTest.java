package io.github.raktimchandra.testharness.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.raktimchandra.testharness.api.auth.Credentials;
import io.github.raktimchandra.testharness.api.auth.TokenCache;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TokenCacheTest {

    @Test
    void logsEachUserInOnceEvenUnderConcurrency() {
        AtomicInteger logins = new AtomicInteger();
        TokenCache cache = new TokenCache(c -> {
            logins.incrementAndGet();
            return "token-" + c.username();
        });
        IntStream.range(0, 200).parallel().forEach(i -> cache.tokenFor(Credentials.of("driver" + (i % 3), "pw")));
        assertThat(logins).hasValue(3);
        assertThat(cache.size()).isEqualTo(3);
        cache.invalidate("driver0");
        assertThat(cache.tokenFor(Credentials.of("driver0", "pw"))).isEqualTo("token-driver0");
        assertThat(logins).hasValue(4);
    }

    @Test
    void passwordsNeverPrint() {
        Credentials c = Credentials.of("clerk1", "s3cret");
        assertThat(c.toString()).doesNotContain("s3cret").contains("clerk1");
        assertThat(c.password().reveal()).isEqualTo("s3cret");
        assertThatThrownBy(() -> new Credentials(null, c.password())).isInstanceOf(NullPointerException.class);
    }
}
