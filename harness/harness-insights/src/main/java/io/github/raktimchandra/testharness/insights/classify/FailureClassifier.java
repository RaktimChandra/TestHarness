package io.github.raktimchandra.testharness.insights.classify;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Classifies a failure by walking its cause chain. Matching is by class <em>name</em>, so the
 * classifier needs no Selenium, Testcontainers or JDBC on the classpath and works on causes
 * recorded as text (see {@link #classify(String, String)}).
 *
 * <p>Order matters: environment problems are checked first (a dead browser also produces
 * timeouts), then locators (a wait that timed out because the element never existed is a
 * locator problem, not a slow page), then data, timeouts and assertions.
 */
public final class FailureClassifier {

    private static final List<String> ENVIRONMENT_TYPES = List.of(
            "SessionNotCreatedException", "NoSuchSessionException", "UnreachableBrowserException",
            "ConnectException", "UnknownHostException", "NoRouteToHostException", "PortUnreachableException",
            "ContainerLaunchException", "ContainerFetchException", "DockerClientException",
            "RetryExhaustedException", "BindException");
    private static final List<String> ENVIRONMENT_TEXT = List.of(
            "could not find a valid docker environment", "err_connection_refused", "connection refused",
            "session not created", "unable to connect", "no space left on device", "gate sut failed to start",
            "out of memory", "chrome not reachable", "browser has closed");
    private static final List<String> LOCATOR_TYPES = List.of(
            "NoSuchElementException", "StaleElementReferenceException", "InvalidSelectorException",
            "ElementNotInteractableException", "ElementClickInterceptedException", "NoSuchFrameException",
            "InvalidElementStateException");
    private static final List<String> DATA_TYPES = List.of(
            "DataIntegrityViolationException", "DuplicateKeyException", "SQLIntegrityConstraintViolationException",
            "ConstraintViolationException");
    private static final List<String> DATA_TEXT = List.of(
            "duplicate key value", "violates unique constraint", "violates foreign key constraint",
            "test data", "already exists");
    private static final List<String> TIMEOUT_TYPES = List.of(
            "TimeoutException", "ConditionTimeoutException", "SocketTimeoutException", "HttpTimeoutException",
            "ScriptTimeoutException");

    private FailureClassifier() {}

    public static FailureCause classify(Throwable failure) {
        if (failure == null) {
            return FailureCause.UNKNOWN;
        }
        List<String> types = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null && types.size() < 20; t = t.getCause() == t ? null : t.getCause()) {
            types.add(t.getClass().getName());
            text.append(' ').append(t.getMessage());
        }
        return classify(types, text.toString());
    }

    /** Classifies from recorded text: the exception class name and its message. */
    public static FailureCause classify(String exceptionType, String message) {
        return classify(List.of(exceptionType == null ? "" : exceptionType), message == null ? "" : message);
    }

    private static FailureCause classify(List<String> types, String rawText) {
        String text = rawText.toLowerCase(Locale.ROOT);
        if (anyType(types, ENVIRONMENT_TYPES) || ENVIRONMENT_TEXT.stream().anyMatch(text::contains)) {
            return FailureCause.ENVIRONMENT;
        }
        if (anyType(types, LOCATOR_TYPES) || text.contains("no such element")) {
            return FailureCause.LOCATOR;
        }
        if (anyType(types, DATA_TYPES) || DATA_TEXT.stream().anyMatch(text::contains)) {
            return FailureCause.DATA;
        }
        if (anyType(types, TIMEOUT_TYPES) || text.contains("timed out")) {
            return FailureCause.TIMEOUT;
        }
        if (types.stream().anyMatch(t -> t.endsWith("AssertionError") || t.endsWith("AssertionFailedError")
                || t.endsWith("MultipleFailuresError") || t.endsWith("ComparisonFailure"))) {
            return FailureCause.ASSERTION;
        }
        return FailureCause.UNKNOWN;
    }

    private static boolean anyType(List<String> types, List<String> simpleNames) {
        return types.stream().anyMatch(t -> simpleNames.stream().anyMatch(n -> t.equals(n) || t.endsWith("." + n)
                || t.endsWith("$" + n)));
    }
}
