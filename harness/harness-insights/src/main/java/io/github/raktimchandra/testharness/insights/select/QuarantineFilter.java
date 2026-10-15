package io.github.raktimchandra.testharness.insights.select;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.insights.TestKeys;
import io.github.raktimchandra.testharness.insights.history.Quarantine;
import java.util.Locale;
import java.util.Set;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the quarantine list ({@code quarantine.file}, default {@code quarantine.json} at the
 * repository root) according to {@code quarantine.mode}:
 * <ul>
 *   <li>{@code off} (default) — run everything; locally you see every failure</li>
 *   <li>{@code exclude} — the blocking CI jobs: quarantined tests are not run</li>
 *   <li>{@code only} — the non-blocking quarantine job: run just the quarantined tests</li>
 * </ul>
 */
public class QuarantineFilter implements PostDiscoveryFilter {

    private static final Logger log = LoggerFactory.getLogger(QuarantineFilter.class);

    enum Mode { OFF, EXCLUDE, ONLY }

    private final Mode mode;
    private final Set<String> quarantined;

    public QuarantineFilter() {
        HarnessConfig config = HarnessConfig.get();
        this.mode = Mode.valueOf(config.string("quarantine.mode", "off").toUpperCase(Locale.ROOT));
        this.quarantined = mode == Mode.OFF ? Set.of()
                : RepoFiles.locate(config.string("quarantine.file", "quarantine.json"))
                        .map(Quarantine::read).orElse(Quarantine.empty()).keys();
        if (mode != Mode.OFF) {
            log.info("Quarantine mode {}: {} quarantined test(s)", mode, quarantined.size());
        }
    }

    QuarantineFilter(Mode mode, Set<String> quarantined) {
        this.mode = mode;
        this.quarantined = quarantined;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (mode == Mode.OFF || !Decidable.is(descriptor)) {
            return FilterResult.included("quarantine off or container");
        }
        boolean isQuarantined = TestKeys.of(descriptor.getSource()).map(quarantined::contains).orElse(false);
        return switch (mode) {
            case EXCLUDE -> FilterResult.includedIf(!isQuarantined, () -> "not quarantined",
                    () -> "quarantined (runs in the non-blocking quarantine job)");
            case ONLY -> FilterResult.includedIf(isQuarantined, () -> "quarantined", () -> "not quarantined");
            case OFF -> FilterResult.included("quarantine off");
        };
    }
}
