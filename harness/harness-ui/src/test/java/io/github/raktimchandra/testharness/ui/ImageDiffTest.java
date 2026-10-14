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
}
