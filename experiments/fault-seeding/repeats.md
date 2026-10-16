# Fault seeding, repeated (3 runs)

Runs (CI run IDs): `38726144795`, `38869649371`, `38875469031`. A cell is *caught in k of 3 runs* with the failed-test counts per run; **bold** marks a pair whose verdict differed between runs.

| bug | contract | api | security | ui | a11y | visual | bdd | perf |
|---|---|---|---|---|---|---|---|---|
| API_SKIPS_CONTAINER_VALIDATION | – | 3/3 (4/4/4) | – | – | – | – | 3/3 (2/2/2) | – |
| CHECK_DIGIT_EDGE_CASE | 3/3 (1/1/1) | 3/3 (4/6/8) | **1/3 (1/0/0)** | 3/3 (1/1/2) | – | – | 3/3 (4/1/1) | **2/3 (0/0/0)** |
| MISSING_AUTHORISATION_CHECK | – | 3/3 (3/3/3) | 3/3 (1/1/1) | – | – | – | 3/3 (1/1/1) | – |
| OFF_BY_ONE_CAPACITY | 3/3 (1/1/1) | 3/3 (3/3/3) | – | – | – | – | 3/3 (1/1/1) | – |
| RACE_ON_LAST_SLOT | – | 3/3 (1/1/1) | – | – | – | – | – | – |
| TIMEZONE_SLOT_TIMES | – | 3/3 (6/6/6) | – | 3/3 (3/3/3) | – | – | 3/3 (1/1/1) | – |

Baselines (no bug): every suite green in every run: yes

Verdicts that changed between runs:
- CHECK_DIGIT_EDGE_CASE × security: caught in 1 of 3
- CHECK_DIGIT_EDGE_CASE × perf: caught in 2 of 3

Detection rate per suite (bugs caught of 6), per run: contract 2/2/2; api 6/6/6; security 2/1/1; ui 2/2/2; a11y 0/0/0; visual 0/0/0; bdd 5/5/5; perf 0/1/1

A cell with failed count 0 but caught (perf) means the suite failed on its gate (Gatling's error-rate assertion), not on a test.

