As a terminal planner, I want each container to have at most one open booking, so that two trucks are never sent for the same container.
If a container already has a booking that is BOOKED, another booking for it (by anyone, for any slot) is refused with DUPLICATE_ACTIVE_BOOKING. After the first booking is cancelled the container can be booked again.
