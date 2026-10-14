package io.github.raktimchandra.testharness.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.raktimchandra.testharness.ui.visual.VisualCheck;
import io.github.raktimchandra.testharness.ui.visual.VisualSettings;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class VisualCheckTest {

    @TempDir
    Path dir;

    private final WebDriver driver = mock(WebDriver.class);

    private static byte[] png(Color c, boolean withBox) throws Exception {
        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, 40, 20);
        if (withBox) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 10, 10);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private WebElement element(byte[] png) {
        WebElement e = mock(WebElement.class);
        when(e.getScreenshotAs(OutputType.BYTES)).thenReturn(png);
        when(e.getRect()).thenReturn(new Rectangle(100, 200, 20, 40));
        return e;
    }

    private VisualSettings settings(boolean update, boolean require) {
        return new VisualSettings(dir.resolve("baselines"), dir.resolve("out"), "chrome-test", 0.01, 0.1, update, require);
    }

    @Test
    void updateWritesTheBaselineThenCompareMatches() throws Exception {
        WebElement e = element(png(Color.WHITE, false));
        assertThat(new VisualCheck(driver, settings(true, false)).matches("login", e))
                .isEqualTo(VisualCheck.Outcome.BASELINE_WRITTEN);
        assertThat(dir.resolve("baselines/chrome-test/login.png")).exists();
        assertThat(new VisualCheck(driver, settings(false, true)).matches("login", e))
                .isEqualTo(VisualCheck.Outcome.MATCHED);
    }

    @Test
    void aRealChangeFailsAndLeavesActualAndDiffForReview() throws Exception {
        new VisualCheck(driver, settings(true, false)).matches("form", element(png(Color.WHITE, false)));
        assertThatThrownBy(() -> new VisualCheck(driver, settings(false, true)).matches("form",
                element(png(Color.WHITE, true))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Visual mismatch 'form': 12.500% of pixels differ");
        assertThat(dir.resolve("out/diff/chrome-test/form-diff.png")).exists();
        assertThat(dir.resolve("out/diff/chrome-test/form-actual.png")).exists();
    }

    @Test
    void ignoredChildRegionsAreMaskedRelativeToTheElement() throws Exception {
        new VisualCheck(driver, settings(true, false)).matches("masked", element(png(Color.WHITE, false)));
        WebElement child = mock(WebElement.class);
        when(child.getRect()).thenReturn(new Rectangle(100, 200, 10, 10)); // the box's page position
        when(driver.findElements(By.id("clock"))).thenReturn(List.of(child));
        assertThat(new VisualCheck(driver, settings(false, true)).matches("masked", element(png(Color.WHITE, true)),
                By.id("clock"))).isEqualTo(VisualCheck.Outcome.MATCHED);
    }

    @Test
    void missingBaselineSkipsLocallyAndFailsWhenRequired() throws Exception {
        WebElement e = element(png(Color.WHITE, false));
        assertThatThrownBy(() -> new VisualCheck(driver, settings(false, false)).matches("new-page", e))
                .isInstanceOf(TestAbortedException.class).hasMessageContaining("candidate written to");
        assertThatThrownBy(() -> new VisualCheck(driver, settings(false, true)).matches("new-page", e))
                .isInstanceOf(AssertionError.class).hasMessageContaining("No visual baseline 'new-page'");
        assertThat(Files.exists(dir.resolve("out/new/chrome-test/new-page.png"))).isTrue();
    }
}
