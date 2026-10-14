package io.github.raktimchandra.testharness.ui.visual;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Perceptual image comparison.
 *
 * <p>Two pixels differ when their colour distance in YIQ space — the metric used by
 * pixelmatch (Kotsarenko &amp; Ramos, "Measuring perceived color difference using YIQ NTSC
 * transmission color space", 2009) — exceeds {@code pixelTolerance} (0..1, where 1 is the
 * distance between black and white). Pixels inside ignore regions never count. The result
 * includes a diff image: unchanged pixels faded to grey, differing pixels in red, ignored
 * regions tinted yellow.
 *
 * <p>Simplified relative to pixelmatch: no anti-aliasing detection; small anti-aliasing
 * noise is absorbed by the tolerance and the mismatch-ratio threshold instead.
 */
public final class ImageDiff {

    /** Maximum possible YIQ delta (black vs white), used to normalise to 0..1. */
    private static final double MAX_YIQ_DELTA = 35215.0;

    private ImageDiff() {}

    public record Result(boolean sizeMismatch, long differingPixels, long comparedPixels, BufferedImage diffImage) {
        public double ratio() {
            return comparedPixels == 0 ? (sizeMismatch ? 1.0 : 0.0) : (double) differingPixels / comparedPixels;
        }
    }

    public static Result compare(BufferedImage expected, BufferedImage actual, List<Rectangle> ignore,
                                 double pixelTolerance) {
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            return new Result(true, Math.max(area(expected), area(actual)), 0, actual);
        }
        int w = actual.getWidth();
        int h = actual.getHeight();
        BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double maxDelta = MAX_YIQ_DELTA * pixelTolerance * pixelTolerance;
        long differing = 0;
        long compared = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = expected.getRGB(x, y);
                int b = actual.getRGB(x, y);
                if (ignored(ignore, x, y)) {
                    diff.setRGB(x, y, blend(fade(b), 0xFFE066));
                    continue;
                }
                compared++;
                if (a != b && yiqDelta(a, b) > maxDelta) {
                    differing++;
                    diff.setRGB(x, y, 0xFF0000);
                } else {
                    diff.setRGB(x, y, fade(b));
                }
            }
        }
        return new Result(false, differing, compared, diff);
    }

    /** Squared YIQ distance between two opaque RGB colours (pixelmatch's colorDelta). */
    static double yiqDelta(int rgb1, int rgb2) {
        double r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
        double r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        double y = (r1 - r2) * 0.29889531 + (g1 - g2) * 0.58662247 + (b1 - b2) * 0.11448223;
        double i = (r1 - r2) * 0.59597799 - (g1 - g2) * 0.27417610 - (b1 - b2) * 0.32180189;
        double q = (r1 - r2) * 0.21147017 - (g1 - g2) * 0.52261711 + (b1 - b2) * 0.31114694;
        return 0.5053 * y * y + 0.299 * i * i + 0.1957 * q * q;
    }

    private static boolean ignored(List<Rectangle> regions, int x, int y) {
        for (Rectangle r : regions) {
            if (r.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private static int fade(int rgb) {
        int grey = (int) (0.3 * ((rgb >> 16) & 0xFF) + 0.59 * ((rgb >> 8) & 0xFF) + 0.11 * (rgb & 0xFF));
        int v = 255 - (255 - grey) / 4; // light grey so red differences stand out
        return (v << 16) | (v << 8) | v;
    }

    private static int blend(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return (r << 16) | (g << 8) | bl;
    }

    private static long area(BufferedImage i) {
        return (long) i.getWidth() * i.getHeight();
    }
}
