As a truck driver, I want the system to reject a booking with a mistyped container number, so that the gate does not wait for a container that does not exist.
Container numbers follow ISO 6346: four letters (the fourth is U, J or Z), six digits and a check digit. A number with a wrong check digit is refused with INVALID_CONTAINER_NUMBER.
