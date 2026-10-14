package io.github.raktimchandra.testharness.ui.visual;

import io.github.raktimchandra.testharness.core.config.HarnessConfig;
import io.github.raktimchandra.testharness.core.config.HarnessEnvironment;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Visual regression settings ({@code visual.*}).
 *
 * @param baselineDir     where approved baselines live (committed to the repository)
 * @param outputDir       where candidates, actuals and diffs are written for review
 * @param platform        baseline set to use, e.g. {@code chrome-ci}; rendering differs by browser,
 *                        OS and installed fonts, so each platform has its own baselines
 * @param maxMismatch     fraction of compared pixels allowed to differ (0..1)
 * @param pixelTolerance  perceptual tolerance per pixel (0..1)
 * @param update          write the current rendering as the new baseline instead of comparing
 * @param requireBaseline fail (rather than skip) when the platform has no baseline yet
 */
public record VisualSettings(Path baselineDir, Path outputDir, String platform, double maxMismatch,
                             double pixelTolerance, boolean update, boolean requireBaseline) {

    public static VisualSettings from(HarnessConfig c, String browser) {
        String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).split(" ")[0];
        return new VisualSettings(
                Path.of(c.string("visual.baseline-dir", "src/test/resources/visual-baselines")),
                Path.of(c.string("visual.output-dir", "target/visual")),
                c.string("visual.platform", browser + "-" + os),
                Double.parseDouble(c.string("visual.max-mismatch", "0.002")),
                Double.parseDouble(c.string("visual.pixel-tolerance", "0.1")),
                "update".equalsIgnoreCase(c.string("visual.mode", "compare")),
                c.bool("visual.require-baseline", c.environment() == HarnessEnvironment.CI));
    }
}
