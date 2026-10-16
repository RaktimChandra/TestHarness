@api @regression
Feature: Gate appointment booking rules
  Truck drivers book a time slot at a terminal gate to drop off or collect a
  shipping container. The terminal limits how many trucks each slot can take,
  checks container numbers, and only lets clerks check trucks in.

  Background:
    Given a gate slot tomorrow at 10:00 with room for 2 trucks

  @smoke
  Scenario: A driver books a slot that has room
    Given I am a driver
    When I book that slot for a valid container
    Then the booking is confirmed with a reference
    And the slot has 1 place left

  Scenario: A slot cannot take more trucks than its capacity
    Given 2 other drivers have already booked that slot
    And I am a driver
    When I book that slot for a valid container
    Then the booking is refused because "SLOT_FULL"

  Scenario Outline: Container numbers must be valid ISO 6346 numbers
    Given I am a driver
    When I book that slot for container "<container>"
    Then the outcome is <outcome>

    Examples:
      | container   | outcome                            |
      | CSQU3054383 | confirmed                          |
      | MSCU1000110 | confirmed                          |
      | HLXJ4820152 | confirmed                          |
      | OOLZ7302641 | confirmed                          |
      | CSQU3054384 | refused because "INVALID_CONTAINER_NUMBER" |
      | CSQX3054383 | refused because "INVALID_CONTAINER_NUMBER" |

  Scenario: A container can only have one open appointment
    Given I am a driver
    And I have booked that slot for a valid container
    When another driver books a different slot for the same container
    Then their booking is refused because "DUPLICATE_ACTIVE_BOOKING"

  Scenario: Drivers cannot see each other's bookings
    Given I am a driver
    And I have booked that slot for a valid container
    When another driver looks up my booking
    Then they are told it does not exist

  Scenario: Cancelling releases the place
    Given I am a driver
    And I have booked that slot for a valid container
    When I cancel my booking
    Then my booking is "CANCELLED"
    And the slot has 2 places left

  Scenario: Bookings cannot be changed in the hour before the slot
    Given a gate slot starting in 30 minutes with room for 1 truck
    And I am a driver
    And I have booked that slot for a valid container
    When I cancel my booking
    Then the change is refused because "CHANGE_CUTOFF_PASSED"
