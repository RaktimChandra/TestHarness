package io.github.raktimchandra.testharness.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.raktimchandra.testharness.ui.page.Locators;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class WaitsTest {

    private final WebDriver driver = mock(WebDriver.class);
    private final Waits waits = new Waits(driver, Duration.ofMillis(300), Duration.ofMillis(10));
    private final By button = Locators.testId("book-submit");

    @Test
    void visibleReturnsOnceTheElementAppears() {
        WebElement e = mock(WebElement.class);
        when(e.isDisplayed()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        when(driver.findElement(button)).thenAnswer(inv -> {
            if (calls.incrementAndGet() < 3) {
                throw new NoSuchElementException("not yet");
            }
            return e;
        });
        assertThat(waits.visible(button)).isSameAs(e);
        assertThat(calls.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void staleElementsAreRetriedNotFatal() {
        WebElement e = mock(WebElement.class);
        AtomicInteger calls = new AtomicInteger();
        when(driver.findElement(button)).thenReturn(e);
        when(e.getText()).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 1) {
                throw new StaleElementReferenceException("re-rendered");
            }
            return " Booked ";
        });
        waits.text(button, "Booked");
        assertThat(calls).hasValue(2);
    }

    @Test
    void timeoutsNameTheCondition() {
        when(driver.findElement(any())).thenThrow(new NoSuchElementException("missing"));
        assertThatThrownBy(() -> waits.visible(button))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("waiting 300 ms for")
                .hasMessageContaining("data-testid=\"book-submit\"");
    }

    @Test
    void countAndAttributeAndHoldsWithin() {
        WebElement row = mock(WebElement.class);
        when(driver.findElements(button)).thenReturn(List.of(row, row));
        assertThat(waits.count(button, 2)).hasSize(2);
        when(driver.findElement(button)).thenReturn(row);
        when(row.getDomAttribute("data-status")).thenReturn("CANCELLED");
        waits.attribute(button, "data-status", "CANCELLED");
        assertThat(waits.withTimeout(Duration.ofMillis(50)).holdsWithin("never", d -> false)).isFalse();
        assertThat(waits.holdsWithin("now", d -> true)).isTrue();
    }

    @Test
    void textContainsRequiresVisibility() {
        WebElement e = mock(WebElement.class);
        when(driver.findElement(button)).thenReturn(e);
        when(e.isDisplayed()).thenReturn(true);
        when(e.getText()).thenReturn("Booked GA-1234ABCD for 10:00");
        assertThat(waits.textContains(button, "GA-1234ABCD")).isSameAs(e);
    }

    @Test
    void locatorsEscapeQuotes() {
        assertThat(Locators.testId("a\"b").toString()).contains("[data-testid=\"a\\\"b\"]");
        assertThat(Locators.testIdPrefix("booking-row-").toString()).contains("[data-testid^=\"booking-row-\"]");
    }
}
