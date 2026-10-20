package io.github.raktimchandra.testharness.ui.page;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

class NavigationTest {

    private final WebDriver driver = mock(WebDriver.class);
    private final WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);

    @Test
    void aRendererTimeoutIsRepeatedExactlyOnce() {
        when(driver.navigate()).thenReturn(navigation);
        doThrow(new TimeoutException("timeout: Timed out receiving message from renderer: -0.000"))
                .when(driver).get("http://x/login");
        Navigation.open(driver, "http://x/login");
        verify(navigation).to("http://x/login");
    }

    @Test
    void aSecondRendererTimeoutFails() {
        when(driver.navigate()).thenReturn(navigation);
        TimeoutException renderer = new TimeoutException("Timed out receiving message from renderer: 30.000");
        doThrow(renderer).when(driver).get("http://x/");
        doThrow(renderer).when(navigation).to("http://x/");
        assertThatThrownBy(() -> Navigation.open(driver, "http://x/")).isSameAs(renderer);
    }

    @Test
    void anyOtherTimeoutFailsAtOnce() {
        TimeoutException slow = new TimeoutException("timeout: page load took longer than 30 s");
        doThrow(slow).when(driver).get("http://x/");
        assertThatThrownBy(() -> Navigation.open(driver, "http://x/")).isSameAs(slow);
        verify(driver, never()).navigate();
    }
}
