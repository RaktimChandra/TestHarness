package io.github.raktimchandra.testharness.ui.visual;

import io.github.raktimchandra.testharness.reporting.Report;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshot comparison with baseline management.
 *
 * <ul>
 *   <li><b>compare</b> (default): screenshot the element, mask ignore-regions, diff against the
 *       platform's baseline; on failure attach baseline, actual and diff to the report.</li>
 *   <li><b>no baseline yet</b>: write a candidate under {@code target/visual/new/}; the test is
 *       skipped with that path in the message, or fails when {@code visual.require-baseline}.</li>
 *   <li><b>update</b> ({@code -Dharness.visual.mode=update}): write the rendering as the new
 *       baseline. Baselines are reviewed and committed like code — that is the approval step.</li>
 * </ul>
 *
 * <p>Before every capture the page is <b>stabilised</b>: the focused element is blurred and
 * carets, transitions and animations are frozen. Focus rings ({@code :focus-visible}) paint only
 * while the browser window has OS focus, which varies with parallel headless sessions, so
 * leaving them in made one baseline differ by 2–3.5 % between CI jobs. The pointer is moved off
 * the page content too (hover styles), and the capture waits two animation frames so the
 * changes are painted. Focus styling is therefore
 * outside what visual checks assert (keyboard access is covered by the UI journeys).
 */
public final class VisualCheck {

    private static final Logger log = LoggerFactory.getLogger(VisualCheck.class);

    private final WebDriver driver;
    private final VisualSettings settings;

    public VisualCheck(WebDriver driver, VisualSettings settings) {
        this.driver = driver;
        this.settings = settings;
    }

    public enum Outcome { MATCHED, BASELINE_WRITTEN }

    /** Compares the element against baseline {@code name}, ignoring the given child regions. */
    public Outcome matches(String name, WebElement element, By... ignore) {
        stabilise();
        BufferedImage actual = read(element.getScreenshotAs(OutputType.BYTES));
        List<Rectangle> regions = regions(element, ignore);
        Path baseline = settings.baselineDir().resolve(settings.platform()).resolve(name + ".png");
        if (settings.update()) {
            write(baseline, actual);
            Report.note("Visual baseline updated: " + baseline);
            return Outcome.BASELINE_WRITTEN;
        }
        if (!Files.exists(baseline)) {
            Path candidate = settings.outputDir().resolve("new").resolve(settings.platform()).resolve(name + ".png");
            write(candidate, actual);
            Report.png("Candidate baseline " + name, png(actual));
            String msg = "No visual baseline '" + name + "' for platform " + settings.platform() + "; candidate written to "
                    + candidate.toAbsolutePath() + " (approve with -Dharness.visual.mode=update)";
            if (settings.requireBaseline()) {
                throw new AssertionError(msg);
            }
            Assumptions.abort(msg);
        }
        BufferedImage expected = read(baseline);
        ImageDiff.Result r = ImageDiff.compare(expected, actual, regions, settings.pixelTolerance());
        Report.parameter("visual." + name + ".mismatch", String.format("%.5f", r.ratio()));
        log.info("Visual {} [{}]: {} of {} pixels differ ({})", name, settings.platform(), r.differingPixels(),
                r.comparedPixels(), String.format("%.4f%%", r.ratio() * 100));
        if (r.sizeMismatch() || r.ratio() > settings.maxMismatch()) {
            Path dir = settings.outputDir().resolve("diff").resolve(settings.platform());
            write(dir.resolve(name + "-actual.png"), actual);
            write(dir.resolve(name + "-diff.png"), r.diffImage());
            Report.png("Baseline " + name, png(expected));
            Report.png("Actual " + name, png(actual));
            Report.png("Diff " + name + " (red = changed)", png(r.diffImage()));
            throw new AssertionError(r.sizeMismatch()
                    ? "Visual mismatch '" + name + "': size " + actual.getWidth() + "x" + actual.getHeight()
                            + " differs from baseline " + expected.getWidth() + "x" + expected.getHeight()
                    : String.format("Visual mismatch '%s': %.3f%% of pixels differ (allowed %.3f%%); diff at %s",
                            name, r.ratio() * 100, settings.maxMismatch() * 100, dir.resolve(name + "-diff.png")));
        }
        return Outcome.MATCHED;
    }

    /** Script that removes run-to-run rendering noise unrelated to the design. */
    static final String STABILISE = """
            if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
            if (!document.getElementById('harness-visual-stabilise')) {
              const s = document.createElement('style');
              s.id = 'harness-visual-stabilise';
              s.textContent = '*,*::before,*::after{caret-color:transparent!important;'
                + 'transition:none!important;animation:none!important}';
              document.head.appendChild(s);
            }
            """;

    /** Resolves after two animation frames, i.e. once the blur and style changes are painted. */
    static final String TWO_FRAMES = """
            const done = arguments[arguments.length - 1];
            requestAnimationFrame(() => requestAnimationFrame(() => done(true)));
            """;

    private void stabilise() {
        if (driver instanceof org.openqa.selenium.JavascriptExecutor js) {
            js.executeScript(STABILISE);
            // The pointer stays where the last click left it; native form controls paint a hover
            // border (Chrome on Windows: 2.1 % of a capture differed). Park it on the page corner.
            try {
                new org.openqa.selenium.interactions.Actions(driver).moveToLocation(0, 0).perform();
            } catch (RuntimeException unsupported) {
                // drivers without pointer actions: nothing was hovered by them either
            }
            js.executeAsyncScript(TWO_FRAMES);
        }
    }

    private List<Rectangle> regions(WebElement root, By... ignore) {
        org.openqa.selenium.Rectangle base = root.getRect();
        List<Rectangle> out = new ArrayList<>();
        for (By by : ignore) {
            for (WebElement e : driver.findElements(by)) {
                org.openqa.selenium.Rectangle r = e.getRect();
                out.add(new Rectangle(r.getX() - base.getX(), r.getY() - base.getY(), r.getWidth(), r.getHeight()));
            }
        }
        return out;
    }

    static BufferedImage read(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static BufferedImage read(Path file) {
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read baseline " + file, e);
        }
    }

    static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void write(Path file, BufferedImage image) {
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
