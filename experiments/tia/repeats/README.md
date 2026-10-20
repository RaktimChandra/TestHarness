# TIA experiment, repeated

The single run in [../README.md](../README.md) was repeated twice on 2026-10-16 (r2, r3) on the
same sandbox machine (2 vCPU), with the working tree at `bfd85da`, by
`tools/tia_experiment.py` (fresh recording each time; `mvn` was the sandbox's offline
wrapper). By then the suites had grown (sign-in throttle and security smoke tests), so the
map has **138 tests × 47 files** instead of 120 × 44; recording took 190.0 s and 190.9 s.
Raw data: `r2/`, `r3/` (`tia-experiment.json`, selections, gzipped Maven logs, the map).

| Seeded bug | Run | Selected (map) | Selected run: tests / failed / s | Full run: tests / failed / s | Saved | Caught |
|---|---|---:|---|---|---:|---|
| OFF_BY_ONE_CAPACITY | r1 | 80 of 120 | 95 / 4 / 160.9 | 138 / 4 / 167.2 | 3.8 % | yes |
| OFF_BY_ONE_CAPACITY | r2 | 83 of 138 | 101 / 4 / 148.3 | 159 / 4 / 170.3 | 12.9 % | yes |
| OFF_BY_ONE_CAPACITY | r3 | 81 of 138 | 99 / 4 / 147.3 | 159 / 4 / 166.1 | 11.3 % | yes |
| CHECK_DIGIT_EDGE_CASE | r1 | 64 of 120 | 73 / 9 / 145.9 | 138 / 10 / 186.2 | 21.6 % | yes |
| CHECK_DIGIT_EDGE_CASE | r2 | 66 of 138 | 78 / 10 / 134.4 | 159 / 14 / 185.2 | 27.4 % | yes |
| CHECK_DIGIT_EDGE_CASE | r3 | 66 of 138 | 78 / 11 / 142.1 | 159 / 7 / 169.1 | 16.0 % | yes |
| TIMEZONE_SLOT_TIMES | r1 | 97 of 120 | 115 / 10 / 167.9 | 138 / 10 / 181.1 | 7.3 % | yes |
| TIMEZONE_SLOT_TIMES | r2 | 102 of 138 | 123 / 10 / 159.9 | 159 / 10 / 177.0 | 9.7 % | yes |
| TIMEZONE_SLOT_TIMES | r3 | 101 of 138 | 122 / 10 / 148.9 | 159 / 10 / 169.4 | 12.1 % | yes |
| RACE_ON_LAST_SLOT | r1 | 73 of 120 | 84 / 1 / 174.1 | 138 / 1 / 184.0 | 5.4 % | yes |
| RACE_ON_LAST_SLOT | r2 | 75 of 138 | 89 / 1 / 151.4 | 159 / 1 / 176.2 | 14.1 % | yes |
| RACE_ON_LAST_SLOT | r3 | 75 of 138 | 89 / 1 / 148.9 | 159 / 1 / 160.4 | 7.2 % | yes |
| MISSING_AUTHORISATION_CHECK | r1 | 39 of 120 | 49 / 5 / 157.6 | 138 / 5 / 190.7 | 17.4 % | yes |
| MISSING_AUTHORISATION_CHECK | r2 | 41 of 138 | 53 / 5 / 116.6 | 159 / 5 / 174.5 | 33.2 % | yes |
| MISSING_AUTHORISATION_CHECK | r3 | 41 of 138 | 53 / 5 / 104.0 | 159 / 5 / 147.1 | 29.3 % | yes |
| API_SKIPS_CONTAINER_VALIDATION | r1 | 73 of 120 | 84 / 6 / 179.8 | 138 / 6 / 215.9 | 16.7 % | yes |
| API_SKIPS_CONTAINER_VALIDATION | r2 | 75 of 138 | 89 / 6 / 145.5 | 159 / 6 / 172.9 | 15.8 % | yes |
| API_SKIPS_CONTAINER_VALIDATION | r3 | 75 of 138 | 89 / 6 / 122.3 | 159 / 6 / 142.4 | 14.1 % | yes |

## What the repeats add

- **18 of 18 selected runs caught their bug.** For every bug except CHECK_DIGIT_EDGE_CASE the
  selected run failed the same number of tests as the full run, in all three runs.
- **The saving stays modest and noisy: 3.8 % to 33.2 %** of Maven wall-clock. The same bug
  moves by up to 16 points between runs (MISSING_AUTHORISATION_CHECK 17.4 / 33.2 / 29.3 %),
  because the fixed cost (JVM, Spring, PostgreSQL container) varies from run to run on a
  shared 2-vCPU machine. A single run is not enough to quote a saving; the range is.
- **The map itself is not stable: 83 vs 81 tests selected for CapacityPolicy.java** in two
  recordings of the same code (r2, r3), 102 vs 101 for SlotTimes.java. Tests with random data
  take different branches and so touch different files. A map recorded once can therefore miss
  a test that would touch the file on another day; this is why CI records the map on every
  push to `main` rather than once.
- **CHECK_DIGIT_EDGE_CASE stays data-dependent**: 9/10, 10/14 and 11/7 failures
  (selected/full). The dedicated remainder-ten tests fail every time, which is what makes it
  caught deterministically.
