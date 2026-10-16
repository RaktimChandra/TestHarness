@portsync
Feature: PortSync keeps the terminal state right when events go wrong
  Events arrive through Kafka at least once, sometimes late, sometimes broken. PortSync must
  apply each one exactly once, ignore stale ones, dead-letter impossible ones with a reason,
  and keep the yard consistent throughout.

  Scenario: A container with a wrong check digit is turned away at the gate
    When the simulator sends a gate-in for a container with a wrong check digit
    Then PortSync dead-letters that event as "INVALID_CHECK_DIGIT"
    And the yard is still consistent

  Scenario: A late event cannot rewrite history
    When the simulator sends a back-dated event
    Then PortSync records that event as "IGNORED_STALE"
    And the yard is still consistent

  Scenario: A crane down too long raises a critical alert that is acknowledged once
    Given a quay crane breaks down with fault code "BDD_SPREADER_JAM"
    Then a critical "CRANE_FAULT_DURATION" alert opens for that crane
    When the operator acknowledges the alert
    Then the alert is "ACKNOWLEDGED"
    And acknowledging it again is refused with "ALERT_STATE_CONFLICT"
