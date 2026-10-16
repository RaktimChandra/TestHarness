# TeamCity run (2025.07 (build 197242))

Branch `phase6`, agent `gh-runner-agent`, settings imported from `.teamcity/settings.kts` by the server.

| build | status | tests | failed | ignored | started | finished | status text |
|---|---|---:|---:|---:|---|---|---|
| TestHarness_Build | SUCCESS | 349 | 0 | 0 | 20261016T104802+0000 | 20261016T105116+0000 | Tests passed: 349 |
| TestHarness_ApiSuite | SUCCESS | 112 | 0 | 0 | 20261016T105233+0000 | 20261016T105327+0000 | Tests passed: 112 |
| TestHarness_BddSuite | SUCCESS | 24 | 0 | 1 | 20261016T105327+0000 | 20261016T105420+0000 | Tests passed: 23, ignored: 1 |
| TestHarness_UiSuite_chrome_1 | FAILURE | 15 | 3 | 0 | 20261016T105420+0000 | 20261016T105525+0000 | Tests failed: 3 (3 new), passed: 12 |
| TestHarness_UiSuite_chrome_2 | FAILURE | 14 | 2 | 0 | 20261016T105525+0000 | 20261016T105629+0000 | Tests failed: 2 (2 new), passed: 12 |
| TestHarness_Perf | SUCCESS | 0 | 0 | 0 | 20261016T105629+0000 | 20261016T105742+0000 | Success |
| TestHarness_Mutation | SUCCESS | 69 | 0 | 0 | 20261016T105742+0000 | 20261016T105827+0000 | Tests passed: 69 |

Failed tests in TestHarness_UiSuite_chrome_1:
- `io.github.raktimchandra.testharness.suites.ui.VisualRegressionTest: Visual regression.login_error(WebDriver, Waits)`: java.lang.AssertionError: Visual mismatch 'login-form-error': 1.569% of pixels differ (allowed 0.200%); diff at target/visual/diff/chrome-ci/login-form-error-diff.png at io.github.raktimchandra.testharness.ui.visual.VisualCheck.matches(VisualCheck.java:94) at io.github.raktimchandra.testharness.suit
- `io.github.raktimchandra.testharness.suites.ui.VisualRegressionTest: Visual regression.cancel_confirmation_dialog(WebDriver, Waits)`: java.lang.AssertionError: Visual mismatch 'cancel-dialog': 1.414% of pixels differ (allowed 0.200%); diff at target/visual/diff/chrome-ci/cancel-dialog-diff.png at io.github.raktimchandra.testharness.ui.visual.VisualCheck.matches(VisualCheck.java:94) at io.github.raktimchandra.testharness.suites.ui.
- `io.github.raktimchandra.testharness.suites.ui.VisualRegressionTest: Visual regression.login_form(WebDriver, Waits)`: java.lang.AssertionError: Visual mismatch 'login-form': 0.669% of pixels differ (allowed 0.200%); diff at target/visual/diff/chrome-ci/login-form-diff.png at io.github.raktimchandra.testharness.ui.visual.VisualCheck.matches(VisualCheck.java:94) at io.github.raktimchandra.testharness.suites.ui.Visual

Failed tests in TestHarness_UiSuite_chrome_2:
- `io.github.raktimchandra.testharness.suites.ui.VisualRegressionTest: Visual regression.booking_form_with_a_check_digit_error(WebDriver, Waits)`: java.lang.AssertionError: Visual mismatch 'booking-form-invalid': 0.904% of pixels differ (allowed 0.200%); diff at target/visual/diff/chrome-ci/booking-form-invalid-diff.png at io.github.raktimchandra.testharness.ui.visual.VisualCheck.matches(VisualCheck.java:94) at io.github.raktimchandra.testharn
- `io.github.raktimchandra.testharness.suites.ui.VisualRegressionTest: Visual regression.clerk_search_form(WebDriver, Waits)`: java.lang.AssertionError: Visual mismatch 'clerk-search-form': 1.685% of pixels differ (allowed 0.200%); diff at target/visual/diff/chrome-ci/clerk-search-form-diff.png at io.github.raktimchandra.testharness.ui.visual.VisualCheck.matches(VisualCheck.java:94) at io.github.raktimchandra.testharness.su
