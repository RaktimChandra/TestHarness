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
 * includes a diff image: unchanged pixels faded to grey, differing pixels in red, pixels
 * discounted as anti-aliasing in orange, ignored regions tinted yellow.
 *
 * <p>Anti-aliasing: a differing pixel is not counted when, in either image, it is an
 * anti-aliased edge pixel — the detector pixelmatch uses (Vysniauskas, "Anti-aliased Pixel and
 * Intensity Slope Detector", 2009): among its 8 neighbours at most two have its exact colour,
 * it lies between a darker and a brighter neighbour, and that darkest or brightest neighbour
 * sits inside a flat area (3+ identical neighbours) in both images. A glyph drawn half a pixel
 * further along produces exactly such pixels (Firefox did, run to run, in CI in October 2026);
 * a changed colour, a moved box or a different word does not, because their pixels are not
 * on a smooth slope between two flat areas.
 */
public final class ImageDiff {

    /** Maximum possible YIQ delta (black vs white), used to normalise to 0..1. */
    private static final double MAX_YIQ_DELTA = 35215.0;

    private ImageDiff() {}

    /**
     * @param differingPixels   pixels that differ and are not anti-aliasing
     * @param antiAliasedPixels pixels that differ only as anti-aliased edges (not counted)
     */
    public record Result(boolean sizeMismatch, long differingPixels, long comparedPixels, BufferedImage diffImage,
                         long antiAliasedPixels) {
        public Result(boolean sizeMismatch, long differingPixels, long comparedPixels, BufferedImage diffImage) {
            this(sizeMismatch, differingPixels, comparedPixels, diffImage, 0);
        }

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
        int[] e = expected.getRGB(0, 0, w, h, null, 0, w);
        int[] a = actual.getRGB(0, 0, w, h, null, 0, w);
        double maxDelta = MAX_YIQ_DELTA * pixelTolerance * pixelTolerance;
        long differing = 0;
        long antiAliased = 0;
        long compared = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pe = e[y * w + x] & 0xFFFFFF;
                int pa = a[y * w + x] & 0xFFFFFF;
                if (ignored(ignore, x, y)) {
                    diff.setRGB(x, y, blend(fade(pa), 0xFFE066));
                    continue;
                }
                compared++;
                if (pe != pa && yiqDelta(pe, pa) > maxDelta) {
                    if (antiAliased(e, a, x, y, w, h) || antiAliased(a, e, x, y, w, h)) {
                        antiAliased++;
                        diff.setRGB(x, y, 0xFFA500);
                    } else {
                        differing++;
                        diff.setRGB(x, y, 0xFF0000);
                    }
                } else {
                    diff.setRGB(x, y, fade(pa));
                }
            }
        }
        return new Result(false, differing, compared, diff, antiAliased);
    }

    /** Whether pixel (x1, y1) of {@code img} is an anti-aliased edge pixel (see the class comment). */
    static boolean antiAliased(int[] img, int[] other, int x1, int y1, int w, int h) {
        int x0 = Math.max(x1 - 1, 0), y0 = Math.max(y1 - 1, 0);
        int x2 = Math.min(x1 + 1, w - 1), y2 = Math.min(y1 + 1, h - 1);
        int centre = img[y1 * w + x1] & 0xFFFFFF;
        int zeroes = (x1 == x0 || x1 == x2 || y1 == y0 || y1 == y2) ? 1 : 0;
        double min = 0, max = 0;
        int minX = 0, minY = 0, maxX = 0, maxY = 0;
        for (int x = x0; x <= x2; x++) {
            for (int y = y0; y <= y2; y++) {
                if (x == x1 && y == y1) {
                    continue;
                }
                double delta = brightnessDelta(centre, img[y * w + x] & 0xFFFFFF);
                if (delta == 0) {
                    zeroes++;
                    if (zeroes > 2) {
                        return false; // three or more identical neighbours: part of a flat area
                    }
                } else if (delta < min) {
                    min = delta;
                    minX = x;
                    minY = y;
                } else if (delta > max) {
                    max = delta;
                    maxX = x;
                    maxY = y;
                }
            }
        }
        if (min == 0 || max == 0) {
            return false; // not between a darker and a brighter neighbour
        }
        return (manySiblings(img, minX, minY, w, h) && manySiblings(other, minX, minY, w, h))
                || (manySiblings(img, maxX, maxY, w, h) && manySiblings(other, maxX, maxY, w, h));
    }

    /** Whether the pixel has at least three neighbours of exactly its colour (edges count as one). */
    private static boolean manySiblings(int[] img, int x1, int y1, int w, int h) {
        int x0 = Math.max(x1 - 1, 0), y0 = Math.max(y1 - 1, 0);
        int x2 = Math.min(x1 + 1, w - 1), y2 = Math.min(y1 + 1, h - 1);
        int centre = img[y1 * w + x1] & 0xFFFFFF;
        int zeroes = (x1 == x0 || x1 == x2 || y1 == y0 || y1 == y2) ? 1 : 0;
        for (int x = x0; x <= x2; x++) {
            for (int y = y0; y <= y2; y++) {
                if ((x != x1 || y != y1) && (img[y * w + x] & 0xFFFFFF) == centre) {
                    zeroes++;
                    if (zeroes > 2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Signed difference in YIQ luma (pixelmatch's colorDelta with yOnly). */
    static double brightnessDelta(int rgb1, int rgb2) {
        return luma(rgb1) - luma(rgb2);
    }

    private static double luma(int rgb) {
        return ((rgb >> 16) & 0xFF) * 0.29889531 + ((rgb >> 8) & 0xFF) * 0.58662247 + (rgb & 0xFF) * 0.11448223;
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
