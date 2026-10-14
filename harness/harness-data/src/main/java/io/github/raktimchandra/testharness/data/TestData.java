package io.github.raktimchandra.testharness.data;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for generated test data. One seed per JVM: random by default, fixed with
 * {@code -Dharness.data.seed=1234} to reproduce a failing run exactly. The seed is logged
 * at start-up so it can always be recovered from a run's logs.
 *
 * <p>Each thread gets its own {@link Faker} (Faker is not thread-safe) derived from the run
 * seed and a thread counter, so parallel tests never share a random stream.
 */
public final class TestData {

    private static final Logger log = LoggerFactory.getLogger(TestData.class);
    private static final long SEED = resolveSeed();
    private static final AtomicLong STREAMS = new AtomicLong();
    private static final ThreadLocal<Random> RANDOM =
            ThreadLocal.withInitial(() -> new Random(SEED * 31 + STREAMS.incrementAndGet()));
    private static final ThreadLocal<Faker> FAKER =
            ThreadLocal.withInitial(() -> new Faker(Locale.ENGLISH, RANDOM.get()));

    private TestData() {}

    public static long seed() {
        return SEED;
    }

    public static Faker faker() {
        return FAKER.get();
    }

    public static Random random() {
        return RANDOM.get();
    }

    private static long resolveSeed() {
        String configured = HarnessConfig.get().string("data.seed", "");
        long seed = configured.isBlank() ? new Random().nextLong() : Long.parseLong(configured.strip());
        log.info("Test data seed: {} (reproduce with -Dharness.data.seed={})", seed, seed);
        return seed;
    }
}
