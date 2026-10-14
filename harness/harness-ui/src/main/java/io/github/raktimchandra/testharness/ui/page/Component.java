package io.github.raktimchandra.testharness.ui.page;

import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * A reusable piece of a page (a table, a dialog, a form) located by a root element. All
 * lookups are scoped to the root, so the same component class works wherever it appears
 * (e.g. the slot picker inside the booking form and inside the reschedule panel).
 */
public abstract class Component {

    protected final WebDriver driver;
    protected final Waits waits;
    protected final By root;

    protected Component(WebDriver driver, Waits waits, By root) {
        this.driver = driver;
        this.waits = waits;
        this.root = root;
    }

    public WebElement rootElement() {
        return waits.visible(root);
    }

    protected WebElement find(By inner) {
        return waits.until(inner + " inside " + root, d -> {
            WebElement e = d.findElement(root).findElement(inner);
            return e.isDisplayed() ? e : null;
        });
    }

    protected List<WebElement> findAll(By inner) {
        return driver.findElement(root).findElements(inner);
    }

    public boolean isDisplayed() {
        List<WebElement> found = driver.findElements(root);
        return !found.isEmpty() && found.getFirst().isDisplayed();
    }
}
