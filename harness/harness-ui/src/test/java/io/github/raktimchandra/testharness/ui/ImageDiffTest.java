package io.github.raktimchandra.testharness.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.ui.visual.ImageDiff;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageDiffTest {

    private static BufferedImage image(Color background, Rectangle box, Color boxColor) {
        BufferedImage img = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, 100, 50);
        if (box != null) {
            g.setColor(boxColor);
            g.fill(box);
        }
        g.dispose();
        return img;
    }

    @Test
    void identicalImagesHaveNoDifferences() {
        BufferedImage a = image(Color.WHITE, new Rectangle(10, 10, 20, 10), Color.BLUE);
        ImageDiff.Result r = ImageDiff.compare(a, image(Color.WHITE, new Rectangle(10, 10, 20, 10), Color.BLUE),
                List.of(), 0.1);
        assertThat(r.differingPixels()).isZero();
        assertThat(r.comparedPixels()).isEqualTo(5000);
        assertThat(r.ratio()).isZero();
    }

    @Test
    void aChangedRegionIsCountedExactly() {
        BufferedImage expected = image(Color.WHITE, null, null);
        BufferedImage actual = image(Color.WHITE, new Rectangle(0, 0, 10, 5), Color.RED);
        ImageDiff.Result r = ImageDiff.compare(expected, actual, List.of(), 0.1);
        assertThat(r.differingPixels()).isEqualTo(50);
        assertThat(r.ratio()).isEqualTo(0.01);
        assertThat(r.diffImage().getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFF0000);
        assertThat(r.diffImage().getRGB(50, 30) & 0xFFFFFF).isNotEqualTo(0xFF0000);
    }

    @Test
    void imperceptibleChangesAreWithinTolerance() {
        BufferedImage expected = image(new Color(200, 200, 200), null, null);
        BufferedImage actual = image(new Color(202, 201, 200), null, null);
        assertThat(ImageDiff.compare(expected, actual, List.of(), 0.1).differingPixels()).isZero();
        assertThat(ImageDiff.compare(expected, actual, List.of(), 0.0).differingPixels()).isEqualTo(5000);
    }

    @Test
    void ignoreRegionsAreExcludedFromTheComparison() {
        BufferedImage expected = image(Color.WHITE, null, null);
        BufferedImage actual = image(Color.WHITE, new Rectangle(0, 0, 10, 5), Color.BLACK);
        ImageDiff.Result r = ImageDiff.compare(expected, actual, List.of(new Rectangle(0, 0, 10, 5)), 0.1);
        assertThat(r.differingPixels()).isZero();
        assertThat(r.comparedPixels()).isEqualTo(5000 - 50);
    }

    @Test
    void differentSizesAreAMismatch() {
        BufferedImage small = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageDiff.Result r = ImageDiff.compare(small, image(Color.WHITE, null, null), List.of(), 0.1);
        assertThat(r.sizeMismatch()).isTrue();
        assertThat(r.ratio()).isEqualTo(1.0);
    }

    /** White left, black right, one grey column between them: an anti-aliased vertical edge. */
    private static BufferedImage edge(int grey) {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                int v = x < 10 ? 255 : x == 10 ? grey : 0;
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    @Test
    void anEdgeRenderedHalfAPixelFurtherIsAntiAliasingNotADifference() {
        // the edge's grey column changes from 128 to 64, as when a glyph moves by a sub-pixel
        ImageDiff.Result r = ImageDiff.compare(edge(128), edge(64), List.of(), 0.1);
        assertThat(r.differingPixels()).isZero();
        assertThat(r.antiAliasedPixels()).isEqualTo(20);
        assertThat(r.diffImage().getRGB(10, 5) & 0xFFFFFF).isEqualTo(0xFFA500);
    }

    @Test
    void aMovedEdgeOrAChangedColourIsStillADifference() {
        BufferedImage moved = edge(128);
        for (int y = 0; y < 20; y++) {
            for (int x = 10; x < 13; x++) {
                moved.setRGB(x, y, 0xFFFFFF); // the black area starts three pixels later
            }
        }
        ImageDiff.Result r = ImageDiff.compare(edge(128), moved, List.of(), 0.1);
        assertThat(r.differingPixels()).isGreaterThanOrEqualTo(40);

        BufferedImage a = image(Color.WHITE, new Rectangle(10, 10, 20, 10), Color.BLUE);
        BufferedImage b = image(Color.WHITE, new Rectangle(10, 10, 20, 10), Color.RED);
        ImageDiff.Result recoloured = ImageDiff.compare(a, b, List.of(), 0.1);
        assertThat(recoloured.differingPixels()).isEqualTo(200);
        assertThat(recoloured.antiAliasedPixels()).isZero();
    }
}
