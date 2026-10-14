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

## Phase 2

### 7. One-command build — sandbox, 2026-10-14

**Method:** as §1 (`mvn -o clean verify`, sandbox machine, local Chrome) on the final Phase 2
code (commit `846dfec`; later commits change documentation only). Counts from the surefire
XML (`<testcase>` elements); wall-clock from Maven's `Total time`.

| Module | Tests | Failed | Errors | Skipped | Σ test time (s) |
|---|---:|---:|---:|---:|---:|
| sut/gate-domain | 65 | 0 | 0 | 0 | 0.4 |
| sut/gate-api (integration 7 + **Pact provider 9**) | 16 | 0 | 0 | 0 | 8.1 |
| harness/harness-core | 43 | 0 | 0 | 0 | 3.9 |
| harness/harness-reporting | 8 | 0 | 0 | 0 | 0.3 |
| harness/harness-data | 75 | 0 | 0 | 0 | 4.8 |
| harness/harness-api | 13 | 0 | 0 | 0 | 1.3 |
| harness/harness-ui (incl. real Chrome: axe, visual stabilisation; browser start policy) | 37 | 0 | 0 | 0 | 17.7 |
| harness/harness-bdd | 3 | 0 | 0 | 0 | 0.1 |
| suites/api-suite (85 functional + **17 security**) | 102 | 0 | 0 | 0 | 97.6 |
| suites/ui-suite (17 journeys + **3 a11y** + **5 visual**) | 25 | 0 | 0 | 5 | 88.8 |
| suites/bdd-suite | 15 | 0 | 0 | 0 | 60.0 |
| **Total (JVM)** | **402** | **0** | **0** | **5** | **283.0** |
| sut/gate-ui (Vitest: 22 unit + **9 Pact consumer**) | 31 | 0 | 0 | 0 | — |
| suites/perf-suite (Gatling, §11) | 1 simulation, 300 requests | 0 KO | — | — | — |

**Wall-clock: 5 min 16 s** (finished 2026-10-14T23:50:20+05:30). The 5 skipped tests are the
visual checks: the committed baselines belong to the CI platforms (`*-ci`), so on this
machine they write candidates and skip (by design, DESIGN D26). They compare and pass in
CI (§9). An earlier sandbox run of commit `76d7e3b` (398 JVM tests, before the browser
start policy's 4 tests) took 5 min 45 s.

### 8. Selenium Grid — workers 1 / 4 / 8 — CI runs 38607826727 (`76d7e3b`) and 38613549892 (`6c03ab5`)

**Method:** `grid` job of `ci.yml`. SUT, PostgreSQL, hub and Chrome/Firefox/Edge nodes in
Docker Compose on one GitHub-hosted runner (**4 vCPU, 15 GiB**, probed in the job); the UI
suite (20 tests: journeys + a11y; visual excluded on Grid) runs from the host against the
Chrome node with JUnit parallelism = workers, twice per worker count. *Maven wall* is the
whole `mvn verify` invocation; *test span* is surefire's `T E S T S` banner to `Results:`
(`.github/scripts/test_span.py`, timestamps from Maven's logger); *Σ test time* sums the
surefire test-case times.

| Run | Rep | Workers | Tests | Maven wall (s) | Test span (s) | Σ test time (s) | Mean per test (s) |
|---|---:|---:|---:|---:|---:|---:|---:|
| 38607826727 | 1 | 1 | 20 | 63.9 | 60.7 | 53.7 | 2.7 |
| 38607826727 | 1 | 4 | 20 | 49.8 | 46.5 | 160.0 | 8.0 |
| 38607826727 | 1 | 8 | 20 | 53.3 | 50.2 | 313.9 | 15.7 |
| 38607826727 | 2 | 1 | 20 | 57.4 | 54.0 | 48.0 | 2.4 |
| 38607826727 | 2 | 4 | 20 | 50.0 | 46.8 | 157.5 | 7.9 |
| 38607826727 | 2 | 8 | 20 | 52.9 | 49.7 | 301.0 | 15.1 |
| 38613549892 | 1 | 1 | 20 | 58.8 | 55.7 | 49.3 | 2.5 |
| 38613549892 | 1 | 4 | 20 | 48.7 | 45.6 | 156.3 | 7.8 |
| 38613549892 | 1 | 8 | 20 | 50.6 | 47.6 | 290.7 | 14.5 |
| 38613549892 | 2 | 1 | 20 | 55.6 | 52.6 | 46.9 | 2.3 |
| 38613549892 | 2 | 4 | 20 | 48.0 | 45.0 | 151.0 | 7.6 |
| 38613549892 | 2 | 8 | 20 | 53.2 | 50.0 | 303.9 | 15.2 |

**What this shows (4 repetitions, 2 runs):** going from 1 to 4 workers cut the test span by
13–23 % (52.6–60.7 s → 45.0–46.8 s), and 8 workers was *slower* than 4 in every repetition
(47.6–50.2 s). Each test got ~3× slower at 4 workers and
~6× slower at 8, i.e. the runner is CPU-bound: one 4-vCPU machine hosts the SUT, PostgreSQL,
the hub, three browser nodes and the test JVM. Parallelism is not the bottleneck here;
hardware is. On a Grid whose nodes have their own CPUs the same suite would scale further,
but **that was not measured**. An earlier run (38606130094, commit `df45ace`, Maven wall
only, one repetition) gave 45 / 34 / 37 s — the same shape.

Cross-browser on the Grid, 4 workers: **Firefox 20/20 passed** (Σ 268.4 s / 274.4 s in the two
runs), **Edge 20/20 passed** (Σ 173.6 s / 164.9 s).

### 9. CI — every job — run 38607826727, commit `76d7e3b`, 2026-10-14

**Method:** as §2 (test-summary annotations; step durations from the GitHub API).

| Job | Tests | Failed / errors | Test step wall-clock |
|---|---:|---:|---:|
| Build + framework & SUT tests (incl. Pact provider, contract drift check) | 256 | 0 / 0 | 108 s |
| API suite | 102 | 0 / 0 | 44 s |
| BDD suite (Chrome) | 15 | 0 / 0 | 38 s |
| UI suite — Chrome (journeys, a11y, **visual vs committed baselines**) | 25 | 0 / 0 | 61 s |
| UI suite — Firefox | 25 | 0 / 0 | 151 s |
| UI suite — Edge | 25 | 0 / 0 | 92 s |
| Docker Compose: start SUT / API suite in external mode | 102 | 0 / 0 | 79 s / 23 s |
| Gatling performance smoke (§11) | 300 requests | 0 KO | 55 s |
| Selenium Grid (§8) | 6 × 20 + 2 × 20 | 0 / 0 | 328 s + 134 s |

**Final Phase 2 commit `6c03ab5` — run 38613549892: all 10 jobs passed** (build 260 tests,
API 102, BDD 15, UI 25 × Chrome/Firefox/Edge, Compose 102, Grid 6 × 20 + 2 × 20, Gatling 300
requests with 0 KO). The run before it (38611150889, docs-only commit `483d9e4`) failed in the
Edge job: four parallel first starts of Edge ended in `SessionNotCreated` after 90 s while
every later start passed — the reason for the browser start policy (DESIGN D13, `846dfec`).

### 10. Accessibility — axe-core, WCAG 2.0/2.1 A + AA rules

**Method:** `AccessibilityTest` scans nine page states per browser; counts from
`target/a11y/*.json` summed by `.github/scripts/a11y_summary.py`.

| Where | Browser | Page states | Critical | Serious | Moderate | Minor |
|---|---|---:|---:|---:|---:|---:|
| CI run 38607826727 | Chrome 154 | 9 | 0 | 0 | 0 | 0 |
| CI run 38607826727 | Firefox 157 | 9 | 0 | 0 | 0 | 0 |
| CI run 38607826727 | Edge 154 | 9 | 0 | 0 | 0 | 0 |
| Sandbox §7 | Chrome 154 | 9 | 0 | 0 | 0 | 0 |

Zero violations means *no automated WCAG A/AA rule failed*, not that the app is fully
accessible (DESIGN D27). The scanner's ability to find violations is itself tested: on a
fixture page it reports `image-alt`, `label` and `color-contrast` and the gate fails
(`RealBrowserTest`).

### 11. Performance smoke — Gatling booking flow

**Method:** `BookingFlowSimulation`, 60 users ramped over 30 s, each doing login → list
slots → book → read booking → cancel (300 requests), against the embedded SUT on the same
machine. Numbers are Gatling's console *Global Information* (all requests). Gates:
p95 < 800 ms, p99 < 1500 ms, errors < 1 %.

| Run | Machine | Requests | KO | p50 | p95 | p99 | max | mean | Throughput | Gates |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| CI 38613549892 (final) | CI runner | 300 | 0 | 5 ms | 73 ms | 78 ms | 83 ms | — | 10 rps | passed |
| CI 38607826727 | CI runner | 300 | 0 | 5 ms | 79 ms | 81 ms | 91 ms | — | 10 rps | passed |
| CI 38606130094 | CI runner | 300 | 0 | 4 ms | 70 ms | 73 ms | 88 ms | — | 10 rps | passed |
| Sandbox §7 | sandbox | 300 | 0 | 9 ms | 99 ms | 110 ms | 182 ms | 27 ms | 10 rps | passed |
| Sandbox, `76d7e3b` | sandbox | 300 | 0 | 10 ms | 101 ms | 112 ms | 179 ms | 28 ms | 10 rps | passed |

Throughput is 10 rps because the injection profile *is* 10 requests/s on average (300
requests over ~30 s), not because the SUT saturated. This is a regression smoke test on a
shared machine, not a capacity measurement (DESIGN D29).

### 12. Visual regression — stability and the two defects it exposed

**Method:** ratios printed by `VisualCheck` (differing / compared pixels) and the CI
failure annotations.

| Situation | booking-form-invalid | Other 4 checks |
|---|---:|---:|
| CI 38597539668 — first baselines, focus ring captured (Chrome / Firefox) | 3.547 % / 1.990 % | pass |
| CI 38599597303 — stabilised, but the slot list above the form varied (Chrome / Firefox / Edge) | 2.758 % / 1.195 % / 2.758 % | pass |
| Sandbox — same effect reproduced (baseline from a visual-only run, compared in a full parallel run) | 0.784 % | 0 px |
| Sandbox — after giving the check its own date, two full parallel runs | **0 px** | **0 px** |
| CI 38607826727 — all 5 checks × Chrome, Firefox, Edge against committed baselines | **pass** | **pass** |

Framework test: a focused input captured against an unfocused baseline differs by 13.8 %
without stabilisation and 0 % with it (`RealBrowserTest`).

### 13. Contract (Pact)

Consumer: 9 interactions generated by the UI's real client (Vitest, §7) and written to
`contracts/pacts/gate-ui-gate-api.json`; CI regenerates it and found no drift in run
38607826727. Provider: 9/9 interactions verified against the real app and PostgreSQL
(`PactProviderTest`, §7 and every CI build).

### 14. Unit-level coverage — sandbox run of §7

**Method:** as §3.

| Module | Line coverage | Branch coverage | Change since §3 |
|---|---:|---:|---|
| sut/gate-domain | 98.4 % (190/193) | 100.0 % (90/90) | — |
| sut/gate-api | 82.1 % (412/502) | 51.6 % (66/128) | +14.0 pts lines (Pact provider tests) |
| harness/harness-core | 91.8 % (268/292) | 80.8 % (101/125) | — |
| harness/harness-data | 90.7 % (156/172) | 85.0 % (51/60) | — |
| harness/harness-api | 94.8 % (146/154) | 87.1 % (54/62) | — |
| harness/harness-ui | 78.1 % (353/452) | 65.9 % (91/138) | +8.0 pts lines (visual, a11y, start policy) |

### 15. Defects found in Phase 2

| Found by | Defect | Fix |
|---|---|---|
| Pact provider verification | SUT timestamps mixed whole seconds with micro/nanoseconds | `5a394b0` |
| Security smoke tests | URLs rejected by the firewall or Tomcat returned bare/HTML 400s, not the error contract | `c98cd77` |
| Grid job | Two deep-link journeys opened the API URL in the browser (`ERR_CONNECTION_REFUSED` on Grid) | `94e5696` |
| Grid job | BiDi console capture failed through the Grid (node advertised the hub's container IP) | `0455bcf` |
| Visual checks in CI | Captures depended on window focus (focus ring) | `f8a001d` |
| Visual checks in CI | A check depended on other tests' data above the component | `bfb55c0` |
| Edge job in CI | Parallel first browser starts failed together (`SessionNotCreated`) | `846dfec` |

### What could not run in Phase 2, and why

| Item | Status | Where it ran instead |
|---|---|---|
| Selenium Grid (hub + nodes) | not run in the sandbox (Docker Hub blocked) | CI `grid` job, §8 |
| Docker Compose stack | not run in the sandbox (same reason) | CI `compose-smoke` job, §9 |
| Edge | no binary in the sandbox | CI (direct and on Grid) |
| Visual comparison against the committed `*-ci` baselines | skipped in the sandbox by design | CI, all three browsers, §9/§12 |
| GitHub artifact / raw log download | blocked by the sandbox's egress policy | failures diagnosed from check-run annotations and local reproduction |
| Grid on separate machines (scaling beyond one 4-vCPU host) | not measured | — |
