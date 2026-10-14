@regression
Feature: Checking trucks in at the gate
  Clerks check a truck in when it arrives. Check-in opens two hours before the
  slot and closes thirty minutes after it ends.

  @api
  Scenario: A clerk checks in a truck inside the window
    Given a gate slot starting in 30 minutes with room for 1 truck
    And a driver has booked that slot
    When a clerk checks the truck in
    Then the booking is "CHECKED_IN"

  @api
  Scenario: Check-in is refused before the window opens
    Given a gate slot tomorrow at 18:00 with room for 1 truck
    And a driver has booked that slot
    When a clerk checks the truck in
    Then the check-in is refused because "CHECKIN_TOO_EARLY"

  @api
  Scenario: Drivers cannot check themselves in
    Given a gate slot starting in 30 minutes with room for 1 truck
    And I am a driver
    And I have booked that slot for a valid container
    When I try to check myself in
    Then the check-in is refused because "FORBIDDEN"

  @ui @smoke
  Scenario: A clerk checks a truck in from the gate screen
    Given a gate slot starting in 30 minutes with room for 1 truck
    And a driver has booked that slot
    When a clerk finds the booking on the gate screen and checks it in
    Then the gate screen shows the booking as checked in

  @ui @smoke
  Scenario: A driver books from the booking screen
    Given a gate slot tomorrow at 09:30 with room for 1 truck
    When a driver books that slot on the booking screen
    Then the booking screen lists it as booked at "09:30–10:00"
