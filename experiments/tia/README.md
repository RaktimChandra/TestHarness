# Test impact analysis experiment

**Question:** if only the tests whose recorded coverage touches the changed file are run,
how much time is saved, and are the seeded bugs still caught?

**Run:** sandbox machine (2 vCPU, see RESULTS.md "Machines"), 2026-10-15, commit `1565a4e`,
by `tools/tia_experiment.py --skip-record` (command line in `tia-experiment.json`).

## Method

1. **Record.** `mvn verify -pl suites/api-suite,suites/ui-suite,suites/bdd-suite
   -Dharness.tia.record=true` attaches the JaCoCo agent to the SUT that the suites start in
   process, resets its counters before each test and dumps them after it
   (`TiaRecorder`, sequential execution enforced). `th tia map` turns the dumps into
   `test-impact-map.json`: for each test, the production source files it executed.
   Result: **120 tests × 44 files**, Σ recorded test time 171.1 s; recording run 202.7 s.
2. **Diff.** For each of the six seeded bugs the "change" is the one file holding the bug's
   branch (`BUG_FILES` in the script).
3. **Select.** `th tia select --files <file>` lists every test that executed that file. It is
   conservative: a test is dropped only when the map proves it never touched the file;
   changes outside the mapped SUT sources (tests, build files) select everything.
4. **Compare.** With the bug switched on (`-Dharness.sut.seeded-bugs=<BUG>`), run the
   selected tests, then the full three suites. Time is the whole Maven invocation
   (start-up, Spring and Testcontainers included); test counts and failures come from the
   surefire XML.

## Results

| Seeded bug | Changed file | Selected tests (map) | Selected run: tests / failed / time | Full run: tests / failed / time | Time saved | Bug caught by selection |
|---|---|---:|---|---|---:|---|
| OFF_BY_ONE_CAPACITY | CapacityPolicy.java | 80 of 120 | 95 / 4 / 160.9 s | 138 / 4 / 167.2 s | 3.8 % | yes |
| CHECK_DIGIT_EDGE_CASE | ContainerNumber.java | 64 of 120 | 73 / 9 / 145.9 s | 138 / 10 / 186.2 s | 21.6 % | yes |
| TIMEZONE_SLOT_TIMES | SlotTimes.java | 97 of 120 | 115 / 10 / 167.9 s | 138 / 10 / 181.1 s | 7.3 % | yes |
| RACE_ON_LAST_SLOT | BookingService.java | 73 of 120 | 84 / 1 / 174.1 s | 138 / 1 / 184.0 s | 5.4 % | yes |
| MISSING_AUTHORISATION_CHECK | BookingAccessPolicy.java | 39 of 120 | 49 / 5 / 157.6 s | 138 / 5 / 190.7 s | 17.4 % | yes |
| API_SKIPS_CONTAINER_VALIDATION | BookingService.java | 73 of 120 | 84 / 6 / 179.8 s | 138 / 6 / 215.9 s | 16.7 % | yes |

"Selected tests (map)" counts map entries (a parameterised method or a Cucumber scenario
outline is one entry); the run columns count executed test cases, so they are larger.

## What this shows

- **Every seeded bug was still caught by the selected subset: 6 of 6.** For five bugs the
  selected run failed exactly the same tests as the full run.
- **The time saved is small: 3.8 % to 21.6 % of wall-clock.** Two reasons, both measured
  above: (1) the selection is large (33 % to 81 % of tests) because most tests go through
  the same booking service and domain policies; (2) a third or more of every run is fixed
  cost (JVM, Spring context, PostgreSQL container) that no selection removes. TIA pays off
  most for changes in narrow code (the authorisation policy: 39 of 120 tests).
- **CHECK_DIGIT_EDGE_CASE is data-dependent.** Test data generates random container numbers;
  about 1 in 11 has a check-digit remainder of 10, which is exactly the case the bug breaks.
  So the selected and full runs failed different tests (9 and 10, overlapping in 5) — each
  run hit the bug through whichever tests happened to draw such a number. The dedicated
  tests (`a_generated_remainder_ten_container_is_accepted` and the UI equivalent) fail every
  time, which is why the bug is caught deterministically.

## Limits

- Coverage is per production **file**, not per method: a change anywhere in
  `BookingService.java` selects every test that ran any of it.
- The map is only as current as its recording; `th tia select` warns when the map's commit
  is not HEAD. CI should re-record on main and select on pull requests.
- One run per mode per bug on a 2-vCPU machine. Timings vary run to run by roughly ±10 s
  (compare the full-run times, which all ran the same 138 tests: 167 s to 216 s).

## Files

- `tia-experiment.json` — every number above, plus the failing test names per run.
- `test-impact-map.json` — the recorded map.
- `selection-<BUG>.txt` — the tests selected for each bug (input to `harness.select`).
- `logs/*.log.gz` — full Maven output of the recording and of all twelve runs.
