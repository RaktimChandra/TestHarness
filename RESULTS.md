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

## Phase 3

### 16. One-command build — sandbox, 2026-10-15

**Method:** as §1 and §7: `mvn -o clean verify`, sandbox machine, local Chrome, on commit
`c275603` (the Phase 3 code; later commits change docs and CI only). Counts from the surefire
XML; wall-clock from Maven's `Total time`.

| Module | Tests | Failed | Errors | Skipped | Σ test time (s) |
|---|---:|---:|---:|---:|---:|
| sut/gate-domain (65 + **4 from surviving mutants**, §18) | 69 | 0 | 0 | 0 | 0.5 |
| sut/gate-api (integration 7 + Pact provider 9) | 16 | 0 | 0 | 0 | 7.7 |
| harness/harness-core | 43 | 0 | 0 | 0 | 3.3 |
| harness/harness-reporting | 8 | 0 | 0 | 0 | 0.3 |
| harness/harness-data | 75 | 0 | 0 | 0 | 4.8 |
| harness/harness-api | 13 | 0 | 0 | 0 | 1.4 |
| harness/harness-ui (+6 self-healing, real Chrome) | 43 | 0 | 0 | 0 | 22.4 |
| harness/harness-bdd | 3 | 0 | 0 | 0 | 0.2 |
| **harness/harness-insights** (new: history, flakiness, filters, TIA, AI — PostgreSQL via Testcontainers, real JaCoCo agent, stub LLM servers) | 35 | 0 | 0 | 0 | 2.0 |
| **harness/harness-cli** (new) | 10 | 0 | 0 | 0 | 1.0 |
| suites/api-suite (85 functional + 17 security) | 102 | 0 | 0 | 0 | 108.8 |
| suites/ui-suite (17 journeys + 3 a11y + 5 visual + **1 healing demo**) | 26 | 0 | 0 | 5 | 96.5 |
| suites/bdd-suite | 15 | 0 | 0 | 0 | 62.1 |
| **Total (JVM)** | **458** | **0** | **0** | **5** | **310.9** |
| sut/gate-ui (Vitest: 22 unit + 9 Pact consumer) | 31 | 0 | 0 | 0 | — |
| suites/perf-suite (Gatling) | 1 simulation, 300 requests | 0 KO | — | — | p95 97 ms · p99 104 ms |
| suites/flaky-demo | skipped by default (`-DskipFlakyDemo=false` runs it; §20) | | | | |

**Wall-clock: 5 min 51 s** (finished 2026-10-15T17:48:44+05:30). The 5 skips are the visual
checks, as in §7. Framework self-tests (all `harness/*` modules): **230**.

### 17. Fault-seeding experiment — CI run 38726144795, 2026-10-15

**Method:** `.github/workflows/fault-seeding.yml`. A matrix job per (bug, suite), plus a
no-bug baseline per suite, on `ubuntu-latest`. Each job switches exactly one bug on with
`-Dharness.sut.seeded-bugs=<BUG>` and runs one suite type. A bug counts as **caught** by a
suite only if at least one of its tests failed **and** the same suite was green in the
baseline, so a pre-existing failure can never be credited. Raw per-job results:
[experiments/fault-seeding/raw](experiments/fault-seeding/raw); table:
[fault-seeding.md](experiments/fault-seeding/fault-seeding.md).

| Seeded bug | contract | api | security | ui | a11y | visual | bdd | perf |
|---|---|---|---|---|---|---|---|---|
| `API_SKIPS_CONTAINER_VALIDATION` | — | **4**/85 | — | — | — | — | **2**/15 | — |
| `CHECK_DIGIT_EDGE_CASE` | **1**/9 | **4**/85 | **1**/17 | **1**/17 | — | — | **4**/15 | — |
| `MISSING_AUTHORISATION_CHECK` | — | **3**/85 | **1**/17 | — | — | — | **1**/15 | — |
| `OFF_BY_ONE_CAPACITY` | **1**/9 | **3**/85 | — | — | — | — | **1**/15 | — |
| `RACE_ON_LAST_SLOT` | — | **1**/85 | — | — | — | — | — | — |
| `TIMEZONE_SLOT_TIMES` | — | **6**/85 | — | **3**/17 | — | — | **1**/15 | — |
| **Detection rate** | 33 % | **100 %** | 33 % | 33 % | 0 % | 0 % | 83 % | 0 % |

Cells: failing tests / tests in the suite; "—" = the suite passed with the bug on (it escaped
that suite). Baseline: every suite green.

**Findings**
- **No bug escaped every suite.** The API suite caught all six.
- **`RACE_ON_LAST_SLOT` was caught by exactly one test** out of 151 functional API, UI and BDD
  tests: the concurrent last-place booking test. Delete that test and the bug ships.
- **`API_SKIPS_CONTAINER_VALIDATION` escaped the UI suite by design:** the UI validates the
  container number client-side, so a browser never sends an invalid one. It is a pure API bug.
- **Pact missed `TIMEZONE_SLOT_TIMES`,** which TEST_STRATEGY expected it to catch: the
  contract matches timestamps by format, and a shifted time has the right format. Recorded
  in TEST_STRATEGY as a strategy error, not hidden.
- **Accessibility, visual and Gatling caught nothing,** correctly: none of the six bugs
  changes markup, appearance or latency.
- Part of `CHECK_DIGIT_EDGE_CASE`'s detection is incidental (random container numbers hit the
  remainder-10 case about 1 time in 11); the dedicated remainder-10 tests catch it every time.

### 18. Mutation testing (PIT) — `sut/gate-domain`, sandbox

**Method:** `mvn -Pmutation verify -pl sut/gate-domain` — PIT 1.30.1, `STRONGER` mutator group,
2 threads, build fails below 95 %.

| Run | Mutants | Killed | Timed out | Survived | No coverage | Score |
|---|---:|---:|---:|---:|---:|---:|
| First run (commit before `95f8686`) | 143 | 135 | 1 | **7** | 0 | 95.1 % |
| After `SurvivingMutantsTest` (4 tests), re-run 2026-10-15 on `c275603` | 143 | 142 | 1 | **0** | 0 | **100 %** |

Re-run: line coverage of mutated classes 190/191, 275 tests run (1.92 per mutant), 29.2 s.
Each survivor was a real gap: the text of a validation message (tests checked only that a
message existed), negative-duration checks per argument, a zero or negative slot length, and
seeded-bug switches defaulting to off. The 95 % gate runs in the CI `mutation` job.

### 19. Test impact analysis — sandbox, 2026-10-15

**Method and full write-up:** [experiments/tia/README.md](experiments/tia/README.md). Per-test
JaCoCo coverage of the in-process SUT recorded once (202.7 s; 120 tests × 44 production
files), then for each seeded bug: run only the tests that executed the changed file, then
the full API + UI + BDD suites, both with the bug on.

| Seeded bug | Selected / full test cases | Selected / full wall-clock | Saved | Caught by selection |
|---|---|---|---:|---|
| OFF_BY_ONE_CAPACITY | 95 / 138 | 160.9 / 167.2 s | 3.8 % | yes |
| CHECK_DIGIT_EDGE_CASE | 73 / 138 | 145.9 / 186.2 s | 21.6 % | yes |
| TIMEZONE_SLOT_TIMES | 115 / 138 | 167.9 / 181.1 s | 7.3 % | yes |
| RACE_ON_LAST_SLOT | 84 / 138 | 174.1 / 184.0 s | 5.4 % | yes |
| MISSING_AUTHORISATION_CHECK | 49 / 138 | 157.6 / 190.7 s | 17.4 % | yes |
| API_SKIPS_CONTAINER_VALIDATION | 84 / 138 | 179.8 / 215.9 s | 16.7 % | yes |

**6 of 6 seeded bugs were still caught; the time saved was 3.8–21.6 %.** The saving is small
because most tests exercise the shared booking service, and because start-up (JVM, Spring,
PostgreSQL container) is a fixed cost no selection removes. One run per mode per bug; the
full runs (identical 138 tests) varied from 167 s to 216 s, so differences under ~10 % are
within noise.

### 20. Flaky-test intelligence — CI run 38730129263, 2026-10-15

**Method and files:** [experiments/flaky-demo](experiments/flaky-demo/README.md). The
deliberately flaky test and its stable control ran 20 times on one commit with history in
PostgreSQL (a service container), then `th history flaky` and `th history quarantine`.

| Test | Runs | Passed | Failed | Flips / same-commit transitions | Score | Quarantined |
|---|---:|---:|---:|---:|---:|---|
| the driver who asks first gets the last place (flaky by design) | 20 | 12 | 8 | 7 / 19 | **0.368** | **yes** (≥ 5 runs, ≥ 0.20) |
| exactly one driver gets the last place (control) | 20 | 20 | 0 | 0 / 19 | 0.000 | no |

All 8 failures were classified **ASSERTION**. With `quarantine.mode=exclude` the blocking
suite ran only the control and passed; with `only`, the quarantine job ran only the flaky
test (which failed in that run) without failing the build.

### 21. Self-healing locators

- **Framework tests (`HealingTest`, 6, real Chrome on a fixture page):** a drifted
  `data-testid` heals by accessible name and the event carries the exact new id; `FAIL` mode
  diagnoses but does not continue; `OFF` mode uses the primary locator only; a primary
  locator matching two elements is a test bug and is never healed; ambiguous fallbacks are
  skipped and the failure lists every attempt with its match count; a working primary is not
  recorded as a heal. All pass (§16).
- **Real app (`SelfHealingDemoTest`):** the test renames the booking button's `data-testid`
  from `book-submit` to `book-button` in the running UI, then books. The step healed
  through the button's accessible name ("Book slot", rank 1), the test is labelled
  `healed` in Allure, and `healing.jsonl` records the fix: *update the Target to
  testId("book-button"), or restore data-testid in the UI*.
- **In the full UI suite (§16) that was the only healing event: 26 tests, 1 healed** — the
  demo. No real test is relying on healing.

### 22. AI-assisted test design

- 7 tests in `AiTest` run against **stub HTTP servers** speaking the OpenAI-compatible
  `/chat/completions` and Anthropic `/v1/messages` formats: request shape and headers for
  both; provider errors reported without leaking the key; no provider configured means no
  client and a message saying how to configure one; Gherkin drafts carry the review banner and
  `@draft`, are validated with the official Gherkin parser and are written only under
  `target/ai-drafts/`; an invalid draft is kept with a warning; the failure summary works with
  no model and, with one, adds a section marked "DRAFT … unverified".
- **Not run against a real model provider:** the authoring environment has no API key. No
  output quality claim is made.

### What could not run in Phase 3, and why
- **AI features against a real provider** — no key in the sandbox (§22).
- **Docker Hub** is blocked in the sandbox, so the PostgreSQL-backed flaky demo, the Grid and
  Compose stacks ran in CI only; `HistoryStoreTest` ran locally against
  `postgres:16-alpine` pre-loaded in the sandbox.
- **GitHub artifact downloads** are blocked, so experiment workflows push their results to a
  branch (`fault-seeding/<run>`, `flaky-demo/<run>`), copied into `experiments/` unchanged.
- The TIA experiment ran once per mode per bug on 2 vCPUs (§19 explains the noise).

## Phase 4

### 23. One-command build — sandbox, 2026-10-16

**Method:** as §16, `mvn -o clean install` on commit `848acb3` (the Phase 4 code before the
coverage fix in §25, which changes only how `harness-insights` forks its test JVMs).

| Module | Tests | Failed | Errors | Skipped | Σ test time (s) |
|---|---:|---:|---:|---:|---:|
| sut/gate-domain | 69 | 0 | 0 | 0 | 0.5 |
| sut/gate-api | 16 | 0 | 0 | 0 | 7.8 |
| harness/harness-core | 43 | 0 | 0 | 0 | 3.4 |
| harness/harness-reporting | 8 | 0 | 0 | 0 | 0.3 |
| harness/harness-data | 75 | 0 | 0 | 0 | 4.1 |
| harness/harness-api | 13 | 0 | 0 | 0 | 1.2 |
| harness/harness-ui | 43 | 0 | 0 | 0 | 21.8 |
| harness/harness-bdd | 3 | 0 | 0 | 0 | 0.1 |
| harness/harness-insights (+ dashboard data, run context, history files) | 39 | 0 | 0 | 0 | 1.8 |
| harness/harness-cli (+ `th dashboard`) | 11 | 0 | 0 | 0 | 1.1 |
| suites/api-suite (+ 3 architecture rules, + 4 worked-example cases) | 109 | 0 | 0 | 0 | 100.3 |
| suites/ui-suite (+ 3 architecture rules) | 29 | 0 | 0 | 5 | 85.5 |
| suites/bdd-suite (+ 2 examples, + 3 architecture rules) | 20 | 0 | 0 | 0 | 61.4 |
| **adapters/portsync** (offline: schema registry 4, architecture 3) | 7 | 0 | 0 | 0 | 3.4 |
| **Total (JVM)** | **485** | **0** | **0** | **5** | **292.5** |
| sut/gate-ui (Vitest) | 31 | 0 | 0 | 0 | — |
| dashboard (Vitest: model + server-rendered App) | 9 | 0 | 0 | 0 | — |
| suites/perf-suite (Gatling) | 1 simulation, 300 requests | 0 KO | — | — | p95 96 ms · p99 106 ms |

**Wall-clock: 5 min 54 s** (finished 2026-10-16T09:51:28+05:30). Framework self-tests: **235**.

### 24. PortSync adapter against the real PortSync stack — CI run 38848816383, 2026-10-16

**Method:** `.github/workflows/portsync.yml` on commit `26c798a`: check out PortSync
(`main` = `f34ac31`), `docker compose up -d --build` (Kafka, PostgreSQL, core, simulator,
dashboard), then `mvn verify -pl adapters/portsync -Dportsync.live` on `ubuntu-latest`.
Counts from the surefire XML via the job summary.

| Suite | Tests | Failed |
|---|---:|---:|
| Offline contract + architecture | 7 | 0 |
| ISO 6346 differential (300 numbers vs the independent oracle, lookups) | 4 | 0 |
| Terminal state consistency | 5 | 0 |
| Event handling through Kafka (DLQ, stale, malformed, duplicates) | 5 | 0 |
| Alert lifecycle (fault → open → ack → 409 → recover → auto-resolve) | 1 | 0 |
| Dashboard UI incl. axe-core | 5 | 0 |
| BDD scenarios (`features/portsync`) | 15 | 0 |
| **Total** | **42** | **0** |

- Test time Σ 98.6 s; the whole job (PortSync image builds included) took **3 min 49 s**.
- **axe-core: 0 violations** of any impact on PortSync's overview, container lookup (with an
  invalid-number verdict showing) and alerts pages.
- **All three ISO 6346 implementations agreed** on every number tried: PortSync's API (300
  generated numbers + 10 BDD examples), its dashboard's live validator (20 numbers) and
  TestHarness's oracle.
- **Found while building it (in TestHarness, not PortSync):** the first CI run reported 24
  tests — surefire's default includes skip the Cucumber runner class, so no scenario ran
  (`26c798a`); and the Gate SUT's Docker build broke because the image did not copy the new
  `adapters/` directory (`c57ab74`).
- **No defect was found in PortSync.** That is a result, not a claim of completeness: these
  42 tests are what was checked.

### 25. Quality dashboard

- `th dashboard` on this machine after §23 and a PIT run: 294 history results in 22 runs (one
  full build + 20 flaky-demo runs + one adapter run), every source present. Page: one
  220 KB `index.html`; two consecutive `npm run build`s produced byte-identical files, which is
  what the CI check relies on. Screenshot: [docs/images/dashboard.png](docs/images/dashboard.png).
- **It found a measurement bug on its first real data:** `harness-insights` showed **9 %**
  line coverage despite 39 passing tests. `TiaTest` resets the real JaCoCo agent per test,
  which also erased every other test class's coverage in that JVM. With one JVM per test
  class (`f3fd133`) the module measures **77.5 % lines / 65.9 % branches** (813/1049,
  331/502).
- Palette checked with the dataviz validator: categorical slots 1–2 pass every check in light
  (worst CVD ΔE 24.7) and dark (26.8) against their surfaces.

### 26. "Add a new test in 5 minutes" — the worked example

The guide's example (J and Z container categories — untested before — plus a direction-echo
test) is committed. Running the guide's own commands in the sandbox:
`verify -pl suites/api-suite -Dtest=BookingApiTest` **54 s** (26 tests, PostgreSQL and app
start included); `verify -pl suites/bdd-suite` **39 s** (17 scenarios). All passed.
Writing the guide also showed the no-sleep / no-stdout ArchUnit rules covered only
`harness/`; they now run in every suite and the adapter (§23).

### What could not run in Phase 4, and why
- **PortSync locally:** it needs Kafka, and Docker Hub (and Maven Central, for building
  PortSync) are blocked in the sandbox. Its suites ran in CI only (§24).
- Visual "awaiting approval" items on the local dashboard are the sandbox's own candidates
  (the committed baselines belong to the CI platforms), as in §7.

## Phase 5

### 27. The pipeline on GitHub Actions — run 38863031687, commit `9151cdc`, 2026-10-16

**Method:** `.github/workflows/ci.yml` on the `phase5` pull request; counts from each job's
test-summary annotation (surefire XML).

| Job | Tests | Failed | Notes |
|---|---:|---:|---|
| Build + framework & SUT tests | — | 0 | as §23 |
| API suite | 109 | 0 | quarantine excluded |
| BDD suite | 20 | 0 | |
| UI — Chrome, shard 1/2 · 2/2 | 15 · 14 | 0 | Σ test time 136.4 s · 128.8 s |
| UI — Firefox, shard 1/2 · 2/2 | 15 · 14 | 0 | 235.4 s · 249.6 s |
| UI — Edge, shard 1/2 · 2/2 | 15 · 14 | 0 | 162.6 s · 214.2 s |
| Docker Compose SUT + API suite | 109 | 0 | |
| Selenium Grid (1/4/8 workers + cross-browser) | 24 per pass | 0 | |
| Gatling, PIT, dashboard, TeamCity DSL | — | 0 | all succeeded |
| Quarantined tests (non-blocking) | 1 | 0 | the flaky demo passed this time; it could not have failed the gate |
| **Quality gate** | | | **success** — every blocking job succeeded |

- **Sharding is exact:** in each browser the two shards ran 15 + 14 = 29 tests, the whole UI
  suite (visual checks included in CI), with the same split in every browser because the shard
  is a function of the test key only.
- **Whole run: 14 min 40 s** from trigger to the gate (06:45:36 → 07:00:16 UTC), 18 jobs.
- The UI shards are uneven in time (e.g. Edge 162.6 s vs 214.2 s): the hash balances test
  *count*, not duration (DESIGN D46).

### 28. The same pipeline on CIForge — run `20261016-053049-a47fe6`, commit `cff831e`

**Method and full table:** [experiments/ciforge](experiments/ciforge/README.md). The 9-build
chain ran on the 2-vCPU sandbox with CIForge's local runner, two builds at a time.

| | |
|---|---|
| Result | **success**, 9 of 9 builds |
| Wall-clock | **8 min 11 s** |
| Quality gate | collected the suites' reports as artifacts: **158 tests, 0 failed, 5 skipped** (the local visual candidates) |
| UI shards | 15 + 14 = 29 |
| PIT | 143/143 mutants killed |
| Gatling | p95 142 ms, p99 298 ms — higher than a solo run (§23: 96 / 106 ms) because two builds shared two vCPUs |

### 29. TeamCity Kotlin DSL — compiled and generated, not run

The `teamcity-dsl` CI job (run 38863031687) compiled `.teamcity/settings.kts` and generated
the project configuration — 16 build configurations: build, API, BDD, six UI shards, Compose
smoke, Grid, Gatling, PIT, quarantined tests, Allure report, and the composite quality gate —
against the DSL library served by a throwaway TeamCity 2025.07 server started in the job.
Getting there took six CI iterations and fixed three real defects in the DSL (DESIGN D49).
**No TeamCity agent ran these builds.**

### What could not run in Phase 5, and why
- **A TeamCity agent** — there is no TeamCity server with agents available; the DSL is compiled
  and generated only (§29).
- **CIForge with the Docker runner for this pipeline** — Docker Hub is blocked in the sandbox,
  so the run used CIForge's local runner (the Docker runner is covered by CIForge's own tests).
