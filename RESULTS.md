# Results

Every number here comes from a run that actually happened, with the method used to get
it. Nothing is estimated or extrapolated. When something has not been measured yet, this
file says so.

## Machines

| Name | Hardware / OS | Software |
|---|---|---|
| **Sandbox** (authoring machine) | 2 vCPU Intel Xeon @ 2.10 GHz, 7.8 GiB RAM, Linux 6.18 | OpenJDK 21.0.12, Maven 3.9.11, Docker Engine 29.8.2, Chrome 154.0.8037.97, Firefox 157.0, Node 22.22 |
| **CI** (GitHub Actions `ubuntu-latest`) | 4 vCPU, 15 GiB RAM (probed 2026-10-14 by `bootstrap-probe.yml`) | Temurin 21, Docker 28.0.4, Chrome 154.0.8037.97, Firefox 157.0, Edge 154.0.4258.53 |

## Phase 1

### 1. One-command build — sandbox, 2026-10-14

**Method:** `mvn -o clean verify` from the repository root (`-Dui.node=system`, local Chrome
paths passed with `-Dharness.ui.*`; offline because the sandbox cannot reach Maven Central —
see "What could not run" below). Counts are `<testcase>` elements in the surefire XML
reports, summed by `.github/scripts/test_summary.py` (the testsuite header under-reports
`@Nested` classes). Wall-clock is Maven's `Total time`.

| Module | Tests | Failed | Errors | Skipped | Σ test time (s) |
|---|---:|---:|---:|---:|---:|
| sut/gate-domain | 65 | 0 | 0 | 0 | 0.6 |
| sut/gate-api (Spring + PostgreSQL integration) | 7 | 0 | 0 | 0 | 6.4 |
| harness/harness-core | 43 | 0 | 0 | 0 | 3.8 |
| harness/harness-reporting | 8 | 0 | 0 | 0 | 0.3 |
| harness/harness-data | 75 | 0 | 0 | 0 | 4.8 |
| harness/harness-api | 12 | 0 | 0 | 0 | 1.3 |
| harness/harness-ui (incl. real Chrome) | 18 | 0 | 0 | 0 | 12.5 |
| harness/harness-bdd | 3 | 0 | 0 | 0 | 0.1 |
| suites/api-suite | 85 | 0 | 0 | 0 | 89.2 |
| suites/ui-suite (Chrome) | 17 | 0 | 0 | 0 | 66.6 |
| suites/bdd-suite | 15 | 0 | 0 | 0 | 62.0 |
| **Total (JVM)** | **348** | **0** | **0** | **0** | **247.6** |
| sut/gate-ui (Vitest, run inside the same build) | 22 | 0 | 0 | 0 | — |

**Wall-clock: 3 min 59 s** (`Total time: 03:59 min`, finished 2026-10-14T20:46:08+05:30).
Σ test time exceeds the suites' share of wall-clock because tests run in parallel
(4 workers API, 2 workers UI/BDD).

Per-module wall-clock from the Maven reactor summary of the same run (s): gate-domain 5.9 ·
gate-ui 10.6 · gate-api 24.5 · core 7.4 · reporting 4.2 · data 8.8 · api 8.6 · ui 18.4 ·
bdd 4.0 · suite-support 0.6 · **api-suite 47.7 · ui-suite 56.7 · bdd-suite 41.1**.

### 2. Cross-browser UI and CI — GitHub Actions run 38589630175, commit `83cf54a`, 2026-10-14

**Method:** the `ci` workflow; counts from each job's test-summary annotation (same script);
durations from the GitHub API's step `started_at`/`completed_at`.

| Job | Tests | Failed / errors | Test step wall-clock |
|---|---:|---:|---:|
| Build + framework & SUT tests | 231 | 0 / 0 | 102 s |
| API suite | 85 | 0 / 0 | 42 s |
| BDD suite (Chrome) | 15 | 0 / 0 | 42 s |
| UI suite — **Chrome** 154 | 17 | 0 / 0 | 53 s |
| UI suite — **Firefox** 157 | 17 | 0 / 0 | 100 s |
| UI suite — **Edge** 154 | 17 | 0 / 0 | 89 s |
| Allure report (merged) | — | — | job 34 s |

Firefox 157 was also run in the sandbox: 17/17 passed (`mvn test -pl suites/ui-suite
-Dharness.ui.browser=firefox`, 1 min 20 s wall-clock for the module).

The Docker Compose job of this run failed at `docker compose up --wait` (image health
check used `/dev/tcp` under dash). After the fix, **run 38590462367 (commit `489f6b6`)
passed every job**, including:

| Job (run 38590462367) | Tests | Failed / errors | Wall-clock |
|---|---:|---:|---:|
| Docker Compose: build image + start PostgreSQL and app until healthy | — | — | 76 s |
| API suite in **external mode** against the container (`HARNESS_ENV=docker`) | 85 | 0 / 0 | 16 s |
| Build, API, BDD, UI × Chrome/Firefox/Edge | 231 / 85 / 15 / 17×3 | 0 / 0 | (same shape as above) |

### 3. Unit-level coverage — sandbox run of §1

**Method:** JaCoCo 0.8.15 `report` goal per module; line and branch counters summed from
`target/site/jacoco/jacoco.csv`. This is each module's coverage **by its own tests only**.
Coverage of SUT code exercised by the black-box suites is measured in Phase 3.

| Module | Line coverage | Branch coverage |
|---|---:|---:|
| sut/gate-domain | 98.4 % (190/193) | 100.0 % (90/90) |
| sut/gate-api | 68.1 % (339/498) | 36.7 % (47/128) |
| harness/harness-core | 91.8 % (268/292) | 80.8 % (101/125) |
| harness/harness-data | 90.7 % (156/172) | 85.0 % (51/60) |
| harness/harness-api | 94.7 % (144/152) | 86.7 % (52/60) |
| harness/harness-ui | 70.1 % (176/251) | 51.4 % (38/74) |

### 4. Allure report — sandbox

**Method:** `mvn -N -Preport verify` on the results of §1. Allure's `summary.json`:
158 test cases, 158 passed, report generated in 2.6 s. (158 = the suites plus the
framework modules that use the Allure JUnit 5 listener; domain and SUT tests do not.)

### 5. Defects found in the SUT while building the suites

| Found by | Defect | Fix |
|---|---|---|
| API suite, strict schema validation | 14 different 4xx responses (validation, type mismatch, unreadable body, 405) lacked the `code` field promised by the error contract, because `spring.mvc.problemdetails.enabled=true` let Spring's advice pre-empt the app's handler | commit `9cc795c` |

### 6. Seeded-bug spot check (not the Phase 3 experiment)

**Method:** `mvn test -pl suites/api-suite -Dtest=BookingApiTest
-Dharness.sut.seeded-bugs=OFF_BY_ONE_CAPACITY`, sandbox, 2026-10-14.
Result: 2 of 22 tests failed — `capacity_is_enforced_at_the_boundary` ("Expected HTTP 409
but was 201") and `concurrent_requests_for_the_last_place_admit_exactly_one`. This only
confirms the switch works end to end; the full detection matrix is Phase 3.

### What could not run, and why

| Item | Status | Reason / where it ran instead |
|---|---|---|
| Edge in the sandbox | not run locally | No Edge binary available; **ran in CI** (17/17). |
| Docker Compose stack in the sandbox | not run locally | Docker Hub is blocked by the sandbox's egress policy (images `maven`, `eclipse-temurin` unavailable); runs in the `compose-smoke` CI job. |
| Online Maven build in the sandbox | not run locally | Maven Central is blocked; the sandbox used a dependency cache produced by the `dev-cache` CI workflow and ran Maven offline. CI builds online. |
| Maven Wrapper download of Maven | not exercised in the sandbox | Same reason; **every CI job runs `./mvnw`**, which downloads Maven 3.9.11. |
