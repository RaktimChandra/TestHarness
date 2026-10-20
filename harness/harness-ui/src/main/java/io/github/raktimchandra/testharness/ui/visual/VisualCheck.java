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
 *
 * <p>During the capture everything except the element and its ancestors is hidden
 * ({@link #ISOLATE}), so a translucent overlay's edges do not pick up the page behind it, and
 * the log line and failure message carry the element's sub-pixel position and the viewport
 * ({@link #GEOMETRY}).
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
        BufferedImage actual;
        String geometry;
        isolate(element);
        try {
            actual = settledCapture(name, element);
            geometry = geometry(element);
        } finally {
            restore();
        }
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
        Report.parameter("visual." + name + ".anti-aliased-pixels", Long.toString(r.antiAliasedPixels()));
        log.info("Visual {} [{}]: {} of {} pixels differ ({}), {} anti-aliased edge pixels not counted; {}", name,
                settings.platform(), r.differingPixels(), r.comparedPixels(), String.format("%.4f%%", r.ratio() * 100),
                r.antiAliasedPixels(), geometry);
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
                    : String.format("Visual mismatch '%s': %.3f%% of pixels differ (allowed %.3f%%) %s; %s; diff at %s",
                            name, r.ratio() * 100, settings.maxMismatch() * 100, changedArea(r.diffImage()),
                            geometry, dir.resolve(name + "-diff.png")));
        }
        return Outcome.MATCHED;
    }

    /** Captures tried before giving up on two identical ones in a row. */
    static final int MAX_CAPTURES = 5;

    /**
     * Captures until two consecutive captures are pixel-identical, waiting a little between
     * them, because a browser can repaint after the page looks ready and a capture taken
     * mid-repaint makes a check flaky rather than wrong. (Suspected, not proven, for Firefox's
     * intermittent cancel-dialog mismatches in CI on 2026-10-20: 0.55 % of pixels, in the
     * heading and button rows, never reproduced locally.)
     */
    private BufferedImage settledCapture(String name, WebElement element) {
        BufferedImage previous = read(element.getScreenshotAs(OutputType.BYTES));
        for (int i = 1; i < MAX_CAPTURES; i++) {
            pause();
            BufferedImage next = read(element.getScreenshotAs(OutputType.BYTES));
            if (samePixels(previous, next)) {
                if (i > 1) {
                    log.info("Visual capture of {} settled after {} captures", name, i + 1);
                }
                return next;
            }
            previous = next;
        }
        log.warn("Visual capture of {} did not settle after {} captures; comparing the last one", name, MAX_CAPTURES);
        Report.note("Visual capture of " + name + " did not settle after " + MAX_CAPTURES + " captures");
        return previous;
    }

    private void pause() {
        if (driver instanceof org.openqa.selenium.JavascriptExecutor js) {
            js.executeAsyncScript(SETTLE);
        }
    }

    static boolean samePixels(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Resolves after 150 ms and two animation frames. */
    static final String SETTLE = """
            const done = arguments[arguments.length - 1];
            setTimeout(() => requestAnimationFrame(() => requestAnimationFrame(() => done(true))), 150);
            """;

    /** Pixels added on every side of a masked element (see {@link #regions}). */
    static final int MASK_MARGIN = 2;

    /**
     * Where the differing (red) pixels are, so a CI failure can be understood from its message
     * alone when the diff image is out of reach: bounding box and pixel count per quarter.
     */
    static String changedArea(BufferedImage diff) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        int w = diff.getWidth(), h = diff.getHeight();
        long[] quarters = new long[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((diff.getRGB(x, y) & 0xFFFFFF) == 0xFF0000) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    quarters[(y < h / 2 ? 0 : 2) + (x < w / 2 ? 0 : 1)]++;
                }
            }
        }
        if (maxX < 0) {
            return "in a " + w + "x" + h + " capture";
        }
        return String.format("in a %dx%d capture, changed area x=%d..%d y=%d..%d, per quarter TL/TR/BL/BR %d/%d/%d/%d",
                w, h, minX, maxX, minY, maxY, quarters[0], quarters[1], quarters[2], quarters[3]);
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
            // Let effects queued by the last interaction run first (a component that focuses an
            // element after paint would otherwise do it after the blur below).
            js.executeAsyncScript(TWO_FRAMES);
            js.executeScript(STABILISE);
            // The pointer stays where the last click left it; native form controls paint a hover
            // border (Chrome on Windows: 2.1 % of a capture differed). Park it on the page corner.
            // Two moves, because a move to where the driver's pointer state already is sends no
            // event: geckodriver does not update that state on WebElement.click(), so a single
            // move to (0, 0) after a click left Firefox's :hover on the clicked input (1.97 % of
            // the focus-ring fixture differed; RealBrowserTest in Firefox).
            try {
                new org.openqa.selenium.interactions.Actions(driver).moveToLocation(1, 1).moveToLocation(0, 0).perform();
            } catch (RuntimeException unsupported) {
                // drivers without pointer actions: nothing was hovered by them either
            }
            js.executeAsyncScript(TWO_FRAMES);
        }
    }

    /**
     * Hides (visibility, so nothing moves) everything on the page except the element, its
     * descendants and its ancestors, for the length of the capture. Where the element is not
     * opaque, whatever lies behind it is part of the capture: a modal dialog centred at a
     * fractional position has rounded corners and top and bottom rows that are partly the
     * backdrop over the page content behind, and that content depends on the scroll position
     * and on what other tests created. In Firefox locally, the same dialog captured at ten
     * scroll positions differed in 0 to 95 edge pixels; with the page hidden, in none (RESULTS §40).
     */
    static final String ISOLATE = """
            const el = arguments[0];
            const hidden = [];
            for (let node = el; node && node !== document.documentElement; node = node.parentElement) {
              const parent = node.parentElement;
              if (!parent) break;
              for (const sibling of parent.children) {
                if (sibling === node || ['SCRIPT', 'STYLE', 'LINK', 'META'].includes(sibling.tagName)) continue;
                hidden.push([sibling, sibling.style.getPropertyValue('visibility'),
                             sibling.style.getPropertyPriority('visibility')]);
                sibling.style.setProperty('visibility', 'hidden', 'important');
              }
            }
            window.__harnessIsolated = hidden;
            """;

    /** Puts back what {@link #ISOLATE} hid. */
    static final String RESTORE = """
            for (const [e, value, priority] of (window.__harnessIsolated || [])) {
              if (value) e.style.setProperty('visibility', value, priority); else e.style.removeProperty('visibility');
            }
            window.__harnessIsolated = [];
            """;

    /**
     * Where the element was drawn, to the sub-pixel, and in what viewport. A rendering can
     * differ only because its position moved by a fraction of a pixel (edges and text are
     * anti-aliased against a different pixel grid), and that is invisible in the images.
     */
    static final String GEOMETRY = """
            const r = arguments[0].getBoundingClientRect();
            const f = (n) => (Math.round(n * 100) / 100).toString();
            return 'drawn at x=' + f(r.x) + ' y=' + f(r.y) + ' size ' + f(r.width) + 'x' + f(r.height)
              + ' in viewport ' + innerWidth + 'x' + innerHeight + ' scrollY=' + f(scrollY) + ' dpr=' + devicePixelRatio;
            """;

    private String geometry(WebElement element) {
        if (driver instanceof org.openqa.selenium.JavascriptExecutor js) {
            try {
                return String.valueOf(js.executeScript(GEOMETRY, element));
            } catch (RuntimeException e) {
                return "geometry unavailable (" + e.getClass().getSimpleName() + ")";
            }
        }
        return "geometry unavailable";
    }

    private void isolate(WebElement element) {
        if (driver instanceof org.openqa.selenium.JavascriptExecutor js) {
            js.executeScript(ISOLATE, element);
            js.executeAsyncScript(TWO_FRAMES);
        }
    }

    private void restore() {
        if (driver instanceof org.openqa.selenium.JavascriptExecutor js) {
            js.executeScript(RESTORE);
        }
    }

    private List<Rectangle> regions(WebElement root, By... ignore) {
        org.openqa.selenium.Rectangle base = root.getRect();
        List<Rectangle> out = new ArrayList<>();
        for (By by : ignore) {
            for (WebElement e : driver.findElements(by)) {
                // Element rects arrive as whole pixels, truncated separately for the element and
                // the root; with fractional layout (a dialog centred in the viewport) the mask can
                // be off by a pixel and expose the anti-aliased edge of text that changes every
                // run. A small margin keeps the mask over the whole element.
                org.openqa.selenium.Rectangle r = e.getRect();
                out.add(new Rectangle(r.getX() - base.getX() - MASK_MARGIN, r.getY() - base.getY() - MASK_MARGIN,
                        r.getWidth() + 2 * MASK_MARGIN, r.getHeight() + 2 * MASK_MARGIN));
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
