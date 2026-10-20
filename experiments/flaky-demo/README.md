# Flaky-test intelligence demo

**Run:** GitHub Actions run 38730129263 (`.github/workflows/flaky-demo.yml`), commit `27a7ad6`
(branch `phase3`), 2026-10-15, 9 min 14 s end to end. Results pushed by the workflow to `flaky-demo/38730129263`
and copied here unchanged.

## Method

`suites/flaky-demo` holds two tests against the real booking API:

- `the driver who asks first gets the last place` — **deliberately flaky.** Two drivers race
  for the last place in a slot and the test asserts that the *first submitted* request
  wins. The API only guarantees that exactly one wins, so the outcome depends on thread
  scheduling.
- `exactly one driver gets the last place` — the correct, stable version of the same check.

The workflow starts PostgreSQL as a service, runs the suite **20 times on the same commit**
with history recording to the database, then runs `th history flaky` and
`th history quarantine --write quarantine.json`, and finally runs the suite with
`quarantine.mode=exclude` (the blocking suite) and `quarantine.mode=only` (the
non-blocking quarantine suite).

## Results

| Test | Runs | Passed | Failed | Same-commit flips | Score | Classified cause | Quarantined |
|---|---:|---:|---:|---:|---:|---|---|
| the driver who asks first gets the last place | 20 | 12 | 8 | 7 of 19 transitions | **0.368** | ASSERTION × 8 | **yes** |
| exactly one driver gets the last place | 20 | 20 | 0 | 0 of 19 | 0.000 | — | no |

- Policy: quarantine at ≥ 5 runs and score ≥ 0.20. Only the flaky test crossed it.
- `quarantine.mode=exclude`: 1 test ran (the stable one) and passed — the blocking suite is
  green without hiding anything.
- `quarantine.mode=only`: 1 test ran (the flaky one) and failed in that run; the job
  reports it and does not fail the build.

## Files

- `history.jsonl` — all 40 results as stored in PostgreSQL (exported by the workflow).
- `flaky-leaderboard.txt`, `quarantine.txt`, `quarantine.json`, `quarantine-modes.txt` —
  CLI output, unedited.

## Repeated (3 runs)

The same workflow was run twice more on 2026-10-16 (runs 38869660669 and 38869821887). Raw
CLI output per run: `runs/<run id>-leaderboard.txt` and `runs/<run id>-quarantine-modes.txt`.

| Run | Flaky test: passed / failed of 20 | Same-commit flips | Score | Stable control | Quarantined |
|---|---:|---:|---:|---|---|
| 38730129263 | 12 / 8 | 7 | 0.368 | 20 / 0, score 0.000 | flaky test only |
| 38869660669 | 11 / 9 | 9 | 0.474 | 20 / 0, score 0.000 | flaky test only |
| 38869821887 | 15 / 5 | 8 | 0.421 | 20 / 0, score 0.000 | flaky test only |

- **The decision was the same in all three runs**: the flaky test crossed the 0.20 threshold
  every time (lowest 0.368) and the control never scored above 0.
- **The failure rate itself moves a lot (25 % to 45 %)**: 20 runs measure a race on a shared
  runner, not a constant. The score is built on flips rather than the failure rate, and the
  flips varied less (7 to 9).
- In every run `quarantine.mode=exclude` ran the control only and passed, and
  `quarantine.mode=only` ran the flaky test only (it failed in each of those three runs).
