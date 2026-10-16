Feature: Scenario hooks under test

  Scenario: a plain scenario gets log context
    Given the log context is recorded

  @ui
  Scenario: a ui scenario that fails
    Given a page in the browser
    Then this step fails
