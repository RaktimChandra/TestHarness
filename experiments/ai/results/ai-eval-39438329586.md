# AI Gherkin drafter: evaluation

Stories: 8; step definitions in the BDD suite: 28; temperature 0.2. Step reuse = drafted steps (in valid drafts) that an existing step definition matches.

| model | attempts | valid Gherkin | scenarios / draft | step reuse | fully runnable drafts | median s / draft |
|---|---:|---:|---:|---:|---:|---:|
| llama3.2:3b | 16 | 11 (68.8 %) | 11.09 | 181 / 499 (36.3 %) | 0 | 55.95 |
| qwen2.5:1.5b | 16 | 14 (87.5 %) | 3.71 | 106 / 245 (43.3 %) | 0 | 23.950000000000003 |
| qwen2.5:7b | 16 | 16 (100.0 %) | 5.5 | 285 / 416 (68.5 %) | 1 | 95.95 |

## Most common steps no definition matches

**llama3.2:3b**: "another driver has booked that slot" (4); "the outcome is "BOOKED"" (4); "the outcome is "CHECKED_IN"" (3); "I book that slot on the booking screen" (2); "I have booked that slot for container "<container>"" (2); "another driver has booked that slot for container "<container>"" (2); "the outcome is "CHANGE_CUTOFF_PASSED"" (2); "the outcome is "DUPLICATE_ACTIVE_BOOKING"" (2)

**qwen2.5:1.5b**: "I book that slot for a valid container "<container>"" (4); "another driver has booked that slot" (4); "I book that slot on the booking screen" (2); "the new slot has room for 2 trucks" (2); "the slot has 0 place(s) left" (2); "the slot has 1 place(s) left" (2); "I am a driver and I have booked that slot for a valid container "<container>"" (1); "I am a terminal planner" (1)

**qwen2.5:7b**: "I am a security officer" (2); "I try to cancel my booking" (2); "another driver books that slot" (2); "the outcome is "RESCHEDULED"" (2); "2 other drivers have already booked that gate slot starting in 30 minutes with room for 1 truck" (1); "I am a gate clerk" (1); "I am refused because "TOO_MANY_ATTEMPTS"" (1); "I attempt to sign in with an incorrect password for "<username>" five times within fifteen minutes" (1)

