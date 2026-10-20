# I broke my own app six ways to see which tests would notice

*Raktim Chandra, October 2026. Data: [experiments/fault-seeding](../../experiments/fault-seeding/fault-seeding.md),
CI run 38726144795; repeated in runs 38869649371 and 38875469031 (see "Update" at the end).*

Test suites are usually judged by how many tests they have or how much code they cover. Neither
tells you whether they would catch the bug you are about to ship. So in
[TestHarness](https://github.com/RaktimChandra/TestHarness) I built the application under test
myself — a container-terminal gate appointment system — and gave it six deliberate defects that
can be switched on at runtime. Then I switched each one on, ran every suite type against it, and
recorded who noticed.

## The setup

The six bugs are the kind that reach production in booking systems:

| Bug | What goes wrong |
|---|---|
| `OFF_BY_ONE_CAPACITY` | a slot accepts capacity + 1 trucks |
| `CHECK_DIGIT_EDGE_CASE` | valid container numbers whose check digit comes from remainder 10 are rejected |
| `TIMEZONE_SLOT_TIMES` | slot times are relabelled instead of converted |
| `RACE_ON_LAST_SLOT` | concurrent requests for the last place all succeed |
| `MISSING_AUTHORISATION_CHECK` | drivers can read and change other drivers' bookings |
| `API_SKIPS_CONTAINER_VALIDATION` | the UI validates container numbers, the API doesn't |

Eight suite types ran once with no bug and once per bug, in parallel CI jobs: the Pact provider
verification (contract), the REST API suite, the security smoke tests, the Selenium UI journeys,
the axe-core accessibility scans, the visual regression checks, the Cucumber BDD scenarios and a
Gatling load test. A suite got credit for a bug only if it **failed with the bug and passed
without it** — a flaky suite cannot take credit for noise. All eight were green on the baseline.

## The result

| Seeded bug | contract | API | security | UI | a11y | visual | BDD | Gatling |
|---|---|---|---|---|---|---|---|---|
| OFF_BY_ONE_CAPACITY | ✔ 1/9 | ✔ 3/85 | – | – | – | – | ✔ 1/15 | – |
| CHECK_DIGIT_EDGE_CASE | ✔ 1/9 | ✔ 4/85 | ✔ 1/17 | ✔ 1/17 | – | – | ✔ 4/15 | – |
| TIMEZONE_SLOT_TIMES | – | ✔ 6/85 | – | ✔ 3/17 | – | – | ✔ 1/15 | – |
| RACE_ON_LAST_SLOT | – | ✔ 1/85 | – | – | – | – | – | – |
| MISSING_AUTHORISATION_CHECK | – | ✔ 3/85 | ✔ 1/17 | – | – | – | ✔ 1/15 | – |
| API_SKIPS_CONTAINER_VALIDATION | – | ✔ 4/85 | – | – | – | – | ✔ 2/15 | – |
| **caught** | 2/6 | **6/6** | 2/6 | 2/6 | 0/6 | 0/6 | 5/6 | 0/6 |

"✔ 3/85" means 3 of the suite's 85 tests failed. Every bug was caught by at least one suite.

## What I learned

**1. The API suite was the cheapest net by far.** It caught all six bugs in about 36 seconds of
CI time (including starting the app), against 50 seconds for 17 browser journeys that caught
two. That is the test pyramid argument, measured instead of asserted.

**2. The UI was blind to the bug the UI itself hides.** With `API_SKIPS_CONTAINER_VALIDATION`
on, the browser still rejects a bad container number before it is sent, so every UI journey
passed while anyone calling the API directly could store garbage. My test strategy predicted
this; it is still unsettling to watch a green UI suite sit on top of a broken API.

**3. One test out of 151 catches the race.** `RACE_ON_LAST_SLOT` was caught only by an API test
that fires six bookings for the last place at the same instant and expects exactly one to win.
UI and BDD tests act one user at a time, so they cannot see concurrency bugs, and the Gatling
run booked slots with plenty of room. If that one test did not exist, the race would ship.

**4. My strategy was wrong about contract tests.** I expected Pact provider verification to
catch the time-zone bug, because the contract includes slot times. It didn't: the consumer
contract matches timestamps by *format* (`datetime` matcher), and a wrongly shifted time is
still a well-formed time. Contract tests verify the shape both sides agree on, not the meaning
of the values. Where the value itself is the contract — a slot time a driver will drive to —
the contract needs an exact value under a fixed provider state, or another test has to own it.

**5. Some catches were luck.** Several `CHECK_DIGIT_EDGE_CASE` failures came from tests that
were not about check digits at all: they generate random container numbers, and roughly one in
eleven needs the remainder-10 rule. On another run those tests would pass. A detection matrix
built from one run with random data overstates coverage. Next step: run each cell with several
data seeds and count only failures that reproduce.

**6. Zero catches is the right answer for some suites.** Accessibility, visual and load tests
caught none of these functional bugs, and should not have: none of the bugs changes markup,
layout or latency. Their value shows up elsewhere: while the suites were being built, the contract tests found a
real timestamp defect in the app, the security tests found another (rejected URLs bypassing
the error contract), and the visual tests exposed two kinds of test instability.
Judging every suite by one bug class would argue them away.

## Caveats

- I wrote the bugs *and* the tests. Even with the tests written first and the expectations
  recorded in advance, that is a bias; independent bugs (or mutation testing, which the project
  also runs on the domain rules — 143 mutants, all killed after one round of new tests) are a
  fairer measure.
- Six bugs is a small sample, and every run was on a shared CI runner.
- The race bug is made reproducible by a deliberate pause inside the critical section; a real
  race is usually rarer and harder to catch.

The workflow, the per-bug results and every failing test with its message are in the
repository, so the experiment can be re-run on any commit.

## Update: the same experiment, three times

Point 5 said a matrix built from one run overstates coverage, so I ran the whole experiment
twice more ([repeats.md](../../experiments/fault-seeding/repeats.md)). Bugs caught of six, per
run:

| | contract | API | security | UI | a11y | visual | BDD | Gatling |
|---|---|---|---|---|---|---|---|---|
| run 1 · 2 · 3 | 2 · 2 · 2 | 6 · 6 · 6 | **2 · 1 · 1** | 2 · 2 · 2 | 0 · 0 · 0 | 0 · 0 · 0 | 5 · 5 · 5 | **0 · 1 · 1** |

Forty-six of the 48 cells gave the same verdict three times, and every baseline was green every
time. The two that moved are both `CHECK_DIGIT_EDGE_CASE`, the bug that depends on random data:

- the security suite caught it in **one run of three** — the lucky catch from point 5, now
  measured;
- **Gatling caught it in two runs of three**, which contradicts point 6 as I first wrote it.
  No Gatling request failed an assertion; the simulation books with generated container
  numbers, and when enough of them needed the broken remainder-10 rule, the rejected bookings
  pushed the error rate over the 1 % gate.

So "zero catches is the right answer" was true of the first run, not of the suite: a load test
that uses realistic data is also a crude functional test. The API suite's six of six and the
race being caught by one test held in every run.
