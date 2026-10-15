package io.github.raktimchandra.testharness.insights.classify;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.raktimchandra.testharness.insights.fixtures.NoSuchElementException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

class FailureClassifierTest {

    static class TimeoutException extends RuntimeException {
        TimeoutException(String m, Throwable cause) {
            super(m, cause);
        }
    }

    static class SessionNotCreatedException extends RuntimeException {
        SessionNotCreatedException(String m) {
            super(m);
        }
    }

    @Test
    void assertionsAreAssertions() {
        assertThat(FailureClassifier.classify(new AssertionFailedError("expected 409 but was 201")))
                .isEqualTo(FailureCause.ASSERTION);
        assertThat(FailureClassifier.classify(new AssertionError("x"))).isEqualTo(FailureCause.ASSERTION);
    }

    @Test
    void aWaitThatNeverFoundItsElementIsALocatorProblemNotATimeout() {
        Throwable wait = new TimeoutException("waiting 10000 ms for By.cssSelector: [data-testid=\"x\"]",
                new NoSuchElementException("no such element"));
        assertThat(FailureClassifier.classify(wait)).isEqualTo(FailureCause.LOCATOR);
    }

    @Test
    void aWaitForSomethingThatExistsIsATimeout() {
        assertThat(FailureClassifier.classify(new TimeoutException("waiting for text 'BOOKED'", null)))
                .isEqualTo(FailureCause.TIMEOUT);
        assertThat(FailureClassifier.classify(new SocketTimeoutException("Read timed out")))
                .isEqualTo(FailureCause.TIMEOUT);
    }

    @Test
    void infrastructureIsEnvironmentEvenWhenItSurfacesAsSomethingElse() {
        assertThat(FailureClassifier.classify(new SessionNotCreatedException("session not created")))
                .isEqualTo(FailureCause.ENVIRONMENT);
        assertThat(FailureClassifier.classify(new IllegalStateException("boom", new ConnectException("refused"))))
                .isEqualTo(FailureCause.ENVIRONMENT);
        assertThat(FailureClassifier.classify(new IllegalStateException(
                "Could not find a valid Docker environment"))).isEqualTo(FailureCause.ENVIRONMENT);
        assertThat(FailureClassifier.classify(new AssertionError("net::ERR_CONNECTION_REFUSED")))
                .as("a browser that cannot reach the app is environment, whatever wrapped it")
                .isEqualTo(FailureCause.ENVIRONMENT);
    }

    @Test
    void dataCollisionsAreData() {
        assertThat(FailureClassifier.classify(new IllegalStateException(
                "ERROR: duplicate key value violates unique constraint \"app_user_username_key\"")))
                .isEqualTo(FailureCause.DATA);
    }

    @Test
    void everythingElseIsUnknown() {
        assertThat(FailureClassifier.classify(new NullPointerException())).isEqualTo(FailureCause.UNKNOWN);
        assertThat(FailureClassifier.classify((Throwable) null)).isEqualTo(FailureCause.UNKNOWN);
    }

    @Test
    void recordedTextCanBeClassifiedToo() {
        assertThat(FailureClassifier.classify("org.openqa.selenium.NoSuchElementException", "no such element"))
                .isEqualTo(FailureCause.LOCATOR);
        assertThat(FailureClassifier.classify("org.opentest4j.AssertionFailedError", "expected: 4"))
                .isEqualTo(FailureCause.ASSERTION);
    }
}
