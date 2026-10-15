package io.github.raktimchandra.testharness.ui.heal;

import java.util.List;
import java.util.Objects;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.locators.RelativeLocator;

/**
 * One way of finding an element. Strategies are ranked from most to least trustworthy:
 * test id, id, accessible name, visible text, position relative to another element.
 */
public sealed interface Strategy {

    List<WebElement> find(SearchContext context);

    /** How a person would write this locator; used in reports and suggested fixes. */
    String describe();

    record TestId(String value) implements Strategy {
        public List<WebElement> find(SearchContext c) {
            return c.findElements(By.cssSelector("[data-testid=\"" + value + "\"]"));
        }

        public String describe() {
            return "data-testid=\"" + value + "\"";
        }
    }

    record Id(String value) implements Strategy {
        public List<WebElement> find(SearchContext c) {
            return c.findElements(By.id(value));
        }

        public String describe() {
            return "id=\"" + value + "\"";
        }
    }

    /**
     * Accessible name computed by the browser (WebDriver "Get Computed Label": aria-label,
     * associated label, button text …) among elements matching {@code css}.
     */
    record AccessibleName(String css, String name) implements Strategy {
        public List<WebElement> find(SearchContext c) {
            return c.findElements(By.cssSelector(css)).stream()
                    .filter(e -> name.equals(Objects.toString(e.getAccessibleName(), "").strip()))
                    .toList();
        }

        public String describe() {
            return "accessible name \"" + name + "\" (" + css + ")";
        }
    }

    record Text(String tag, String text) implements Strategy {
        public List<WebElement> find(SearchContext c) {
            return c.findElements(By.xpath(".//" + tag + "[normalize-space(.)=" + xpathLiteral(text) + "]"));
        }

        public String describe() {
            return tag + " with text \"" + text + "\"";
        }
    }

    /** The {@code tag} element nearest below the anchor; weakest strategy, ranked last. */
    record Below(String tag, Strategy anchor) implements Strategy {
        public List<WebElement> find(SearchContext c) {
            List<WebElement> anchors = anchor.find(c);
            if (anchors.size() != 1) {
                return List.of();
            }
            List<WebElement> below = c.findElements(RelativeLocator.with(By.tagName(tag)).below(anchors.get(0)));
            return below.isEmpty() ? List.of() : List.of(below.get(0));
        }

        public String describe() {
            return "nearest " + tag + " below " + anchor.describe();
        }
    }

    private static String xpathLiteral(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
