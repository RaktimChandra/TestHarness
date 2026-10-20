As a truck driver, I want to move my booking to another slot, so that I can keep my container booked when I am running late.
Rescheduling moves the booking to the new slot if it has room, frees the place in the old slot, and keeps the same booking reference. Moving to a full slot is refused with SLOT_FULL; moving to the same slot is refused with SAME_SLOT.
