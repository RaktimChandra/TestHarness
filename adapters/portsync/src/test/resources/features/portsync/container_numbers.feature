@portsync
Feature: Container numbers at PortSync
  PortSync rejects any event whose container number fails the ISO 6346 check digit, so its
  validator must agree with the standard on every number, including the awkward ones.

  Scenario Outline: The check digit decides whether a number is valid
    When I ask PortSync to validate "<number>"
    Then PortSync says the number is <verdict>
    And the expected check digit is <digit>

    Examples: valid numbers in every equipment category
      | number      | verdict | digit |
      | CSQU3054383 | valid   | none  |
      | MSKU1234565 | valid   | none  |
      | TGHJ0001239 | valid   | none  |
      | APZZ9999990 | valid   | none  |

    Examples: the remainder-ten edge case, where the check digit is 0
      | number      | verdict | digit |
      | QMLU5134800 | valid   | none  |
      | QMLU5134801 | invalid | 0     |

    Examples: one wrong digit, and malformed input
      | number      | verdict | digit |
      | CSQU3054384 | invalid | 3     |
      | MSKU1234566 | invalid | 5     |
      | CSQU305438  | invalid | none  |
      | CSQX3054383 | invalid | none  |

  Scenario: An operator's lower-case, spaced input is accepted
    When I ask PortSync to validate " csqu 3054383 "
    Then PortSync says the number is valid
    And PortSync normalised it to "CSQU3054383"

  Scenario: A valid number that never came through the gate is unknown, not invalid
    When I look up a valid container number PortSync has never seen
    Then PortSync answers 404 with code "CONTAINER_NOT_FOUND"
