# Results

Every number here comes from a run that actually happened, with the method used to get
it. Nothing is estimated or extrapolated. When something has not been measured yet, this
file says so.

## Machines

| Name | Hardware / OS | Software |
|---|---|---|
| **Sandbox** (authoring machine) | 2 vCPU Intel Xeon @ 2.10 GHz, 7.8 GiB RAM, Linux 6.18 | OpenJDK 21.0.12, Maven 3.9.11, Docker Engine 29.8.2, Chrome 154.0.8037.97, Firefox 157.0, Node 22.22 |
| **CI** (GitHub Actions `ubuntu-latest`) | 4 vCPU, 15 GiB RAM (probed 2026-10-14 by `bootstrap-probe.yml`) | Temurin 21, Docker 28.0.4, Chrome 154.0.8037.97, Firefox 157.0, Edge 154.0.4258.53 |
| **CI from Phase 7** (`ubuntu-24.04`, images 20261012 and 20261018) | 4 vCPU; AMD EPYC 7763 / 9V45 / 9V74, Intel Xeon 8573C / 6973P (recorded per job, §46) | browsers pinned: Chrome 155.0.8059.84, Firefox 158.0, Edge 155.0.4283.62 (DESIGN D69) |

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
  changes markup, appearance or latency. *(Repeated in Phase 6, §38: Gatling caught
  `CHECK_DIGIT_EDGE_CASE` in two of three runs through its error-rate gate, and security
  caught it in one of three.)*
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

*Repeated twice in Phase 6 (§38) and run on a real pull request (§44).*

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

*Repeated twice in Phase 6 (§38): quarantined in all three runs.*

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
**No TeamCity agent ran these builds** in Phase 5; in Phase 6 they ran on a real server and agent (§37).

### What could not run in Phase 5, and why
- **A TeamCity agent** — there is no TeamCity server with agents available; the DSL is compiled
  and generated only (§29). *Closed in Phase 6: §37.*
- **CIForge with the Docker runner for this pipeline** — Docker Hub is blocked in the sandbox,
  so the run used CIForge's local runner (the Docker runner is covered by CIForge's own tests).

## Phase 6

### 30. One-command build — sandbox, 2026-10-20

**Method:** as §16, `mvn -o clean install` on commit `57c48a1` (version 1.1.0); counts from the
surefire XML by `.github/scripts/test_summary.py`.

| Module | Tests | Failed | Errors | Skipped | Σ test time (s) |
|---|---:|---:|---:|---:|---:|
| sut/gate-domain | 69 | 0 | 0 | 0 | 0.4 |
| sut/gate-api (+ throttle unit and integration tests, security headers, caching) | 26 | 0 | 0 | 0 | 8.3 |
| harness/harness-core (+ relaxed environment keys) | 44 | 0 | 0 | 0 | 3.2 |
| harness/harness-reporting | 8 | 0 | 0 | 0 | 0.2 |
| harness/harness-data | 75 | 0 | 0 | 0 | 4.5 |
| harness/harness-api | 13 | 0 | 0 | 0 | 1.1 |
| harness/harness-ui (+ known accessibility issues, anti-aliasing, settled captures, navigation) | 54 | 0 | 0 | 0 | 20.8 |
| harness/harness-bdd (+ hooks through the real Cucumber engine) | 4 | 0 | 0 | 0 | 2.6 |
| harness/harness-insights (+ duration shard plan) | 49 | 0 | 0 | 0 | 5.4 |
| harness/harness-cli (+ every command against a fake repository) | 19 | 0 | 0 | 0 | 6.3 |
| suites/api-suite (+ brute force, no enumeration, headers, caching) | 112 | 0 | 0 | 0 | 100.0 |
| suites/ui-suite | 29 | 0 | 0 | 5 | 86.7 |
| suites/bdd-suite (+ drafted-step coverage) | 24 | 0 | 0 | 1 | 58.0 |
| adapters/portsync (offline: schemas, architecture, consumer pact) | 8 | 0 | 0 | 0 | 10.0 |
| **Total (JVM)** | **534** | **0** | **0** | **6** | **307.4** |
| sut/gate-ui (Vitest) | 32 | 0 | 0 | 0 | — |
| dashboard (Vitest, run separately) | 9 | 0 | 0 | 0 | — |
| suites/perf-suite (Gatling) | 1 simulation, 300 requests | 0 KO | — | — | p95 92 ms · p99 98 ms |
| tools/tests (Python, `unittest`) | 5 | 0 | 0 | 0 | — |

**Wall-clock: 6 min 32 s** (Maven total time; the whole command 16:14:04 → 16:20:38 UTC). Skipped: the 5 visual checks (they run
in CI only, §12) and `DraftStepCoverageTest.reportOnDrafts`, which runs only when
`-Dai.drafts` points at drafts to score (§43).

### 31. The pipeline on `main` — run 39410820490, commit `768ee4d` (the Phase 6 merge), 2026-10-20

**Method:** `ci.yml` on the push to `main`; counts from each job's test-summary annotation.
`main` runs three jobs pull requests do not: `tia-map`, the `publish` job, and history-based
sharding for the next pipelines.

| Job | Tests | Failed | Notes |
|---|---:|---:|---|
| Build + framework & SUT tests | 353 | 0 | |
| API suite · Docker Compose + API suite | 112 · 112 | 0 · 0 | |
| BDD suite | 24 | 0 | 1 skipped (drafted-step report) |
| UI — Chrome · Edge · Firefox, 2 shards each | 15 + 14 each | **1** | Firefox shard 1: `cancel-dialog` visual check, 2.389 % of pixels (§40) |
| Selenium Grid (24 per browser), Gatling (300 requests, p95 70 ms), PIT, dashboard, TeamCity DSL | — | 0 | |
| Test impact map | 138 tests × 47 SUT files | — | stored on `ci-history` |
| Allure report | — | — | **failed with no message**; passed on re-run — cause not found |
| Quality gate | | | **failure** (attempt 1) → **success** after "re-run failed jobs" (attempt 2) |
| Publish | | | ran after both attempts: `ci-history` and `gh-pages` created |

- Attempt 1: 10:51:01 → 11:06:17 UTC (15 min 16 s). The Selenium Grid job is the long pole
  (10 min 46 s); every other job finished by 10:58:27.
- Attempt 2 re-ran only the Firefox shard, the Allure report, the gate and the publish job
  (12:05:44 → 12:09:16); all green.
- Every blocking job other than that Firefox shard passed in attempt 1. The re-run is recorded
  rather than hidden: the second publish is also where the duplicated-history bug in §39 came
  from.

### 32. Dependency vulnerabilities (OSV-Scanner) and the ZAP baseline — `security.yml`

**Method:** OSV-Scanner 2.x over a CycloneDX SBOM that Maven resolves for the whole reactor
(test scope included) plus the npm lockfiles; OWASP ZAP baseline (`zap-stable` image) against
the SUT started from its jar. Both fail the job on any finding not triaged in
`osv-scanner.toml` / `.zap/rules.tsv` (DESIGN D54, D55).

| Scan | First run | After the fixes | Green runs |
|---|---|---|---|
| OSV, Maven + npm | **41 advisories (9 critical, 13 high) in 18 transitive packages** — jackson 2.21.4, netty 4.1.135, httpclient5 5.5.2, commons-lang3 3.17.0, commons-compress 1.24.0, opentelemetry 1.49.0 and others | second scan: 8 packages left; floors added for 5 of them (Tomcat, httpcore5, log4j-api, freemarker, PostgreSQL JDBC). **0 untriaged**; 3 Spring Framework 6.2.19 advisories ignored until 2027-01-31 | runs on `bfd85da`, `22743d4`, `d26ea7d`, `88fd713` |
| ZAP baseline | **8 alert types** | 6 header findings **fixed in the SUT** (CSP directives without `default-src` fallback and inline styles, Permissions-Policy, COOP, COEP, CORP); 3 informational rules triaged with reasons | same runs |

- The three ignored Spring advisories are fixed only in Spring 7.0.9; 6.2.19 is the last
  open-source 6.2 release and the project stays on Spring Boot 3 (DESIGN D22). Each needs a
  feature this repository does not use (SSE view fragments, `XsltView`, WebFlux functional
  endpoints), checked by searching the code. For the WebFlux one, `mvn dependency:tree
  -Dincludes=org.springframework:spring-webflux` (sandbox, 2026-10-20) shows it only in
  `gate-api`, in **test** scope, through Pact's `junit5spring`; no `RouterFunction` exists.
- One floor had to be corrected: Tomcat 10.1.58 is not published on Maven Central, so the
  first version of the floor failed to resolve; it is 10.1.61 (`fdf5ae2`).
- The strict CSP was checked by the UI suite: 29 tests in Chrome pass under it.
- **The scanner image itself broke the gate once.** On 2026-10-20 a new `zaproxy:stable`
  image failed before scanning (`Failed to access summary file /home/zap/zap_out.json`,
  run 39410820542). ZAP is now pinned to 2.16.1; its "ZAP is out of date" rule is triaged with
  that reason, and run 39422919352 passed: 63 rules pass, 0 warnings.

### 33. Sign-in throttling — new SUT behaviour, tested from three sides

Five failed sign-ins per username, or 100 per client address, within 15 minutes lock that key
for 5 minutes (`429 TOO_MANY_ATTEMPTS`, `Retry-After`; DESIGN D53).

| Level | Test | Result |
|---|---|---|
| Unit (manual clock) | `LoginThrottleTest` — 9 tests: the fifth failure locks, failures outside the window do not count, usernames compared case- and whitespace-insensitively, success clears the username but not the client, one client trying many usernames is locked, unknown usernames lock like real ones, idle keys are cleaned up, Retry-After rounding | pass |
| Integration over HTTP | `GateApiIntegrationTest` — after five failures the correct password gets 429 `TOO_MANY_ATTEMPTS` with `Retry-After` 290–300 s; other accounts still sign in | pass |
| Black box (api-suite) | `SecuritySmokeTest` — five failures lock the account even against the right password, and the lock response for a non-existent account is identical (no enumeration through the lock) | pass |
| UI unit (Vitest) | `LoginPage.test.jsx` — the 429 message | pass |

**Found while testing it:** Apache HttpClient 5, which `TestRestTemplate` uses, honours
`Retry-After` on a 429 by sleeping and retrying. The first integration test therefore waited
the whole 300 s lock. It now uses the JDK `HttpClient`, which does not retry.

### 34. Windows — `windows.yml` on `windows-2025`, run 39408840976, commit `dc4c3e7`

**Method:** the commands of [docs/WINDOWS.md](docs/WINDOWS.md) in PowerShell with `mvnw.cmd`
and `th.cmd`; the UI suite in Chrome and Edge on Windows. Hosted Windows runners cannot run
Linux containers, so the runner's PostgreSQL stands in for Testcontainers through
`-Dharness.db.url`. Skipped tests are listed by name in the job's annotation.

| | |
|---|---|
| Result | **success**, 10 min 1 s (10:34:00 → 10:44:01 UTC) |
| JVM tests | **492, 0 failed, 20 skipped** |
| Skipped | 5 visual checks (no Windows baselines: baselines are rendered per platform on Linux, so the job writes Windows candidates instead); 14 BDD scenarios outside `@smoke` (the guide's BDD step runs the smoke tag); `DraftStepCoverageTest.reportOnDrafts` (needs `-Dai.drafts`) |
| Not run on Windows | `gate-api`'s 26 tests (Testcontainers integration tests; they run in the Linux build job) |

Earlier green runs: 38890845411 (`d26ea7d`, 490 tests) and 39406598745 (`88fd713`); latest
39452849947 (`d303237`): **500 tests, 0 failed**, the same 20 skips.

**One Edge failure on Windows** (run 39449323500): two journeys stopped while opening a page
with `Timed out receiving message from renderer` — EdgeDriver lost contact with a renderer
process on the loaded runner, before the page under test was involved. Navigations now repeat
once in exactly that case, logged and reported (DESIGN D66); every other timeout and every
assertion still fails at once.

It found four problems that Linux CI could not: `install -DskipTests` still ran the Gatling
simulation; a framework test assumed a container; `cmd.exe` split `-Dname=value` arguments
at `=` when `th.cmd` called `mvnw.cmd` (fixed by quoting, `ProcessesTest`); and a 2.1 %
visual diff from a focus ring under the parked pointer, fixed by moving the pointer away and
waiting two frames before capture.

### 35. Coverage gaps closed — sandbox

| Module | Lines before | Lines after | What was added |
|---|---:|---:|---|
| `harness-cli` | 51 % | **90.7 %** | every command that runs Maven, serves pages, maps or selects TIA, drafts through a model or ingests into PostgreSQL, against a fake repository whose Maven Wrapper records its arguments (`sh` and `cmd` versions) |
| `harness-bdd` | 48.6 % | **100 %** | `ScenarioHooks` through the real Cucumber engine, including evidence capture for a failing `@ui` scenario in a real browser |
| `harness-insights` | 78.3 % | **85.6 %** | the JaCoCo-recorder test moved to its own class — its agent reset was erasing the coverage of the other tests in the same JVM — plus failure summaries and `RepoFiles` |

One real bug turned up: a failure without a message was summarised as an empty
`message:` line; it now reads `(none)`.

### 36. PortSync — contract, SLA scenarios and two accessibility defects in PortSync

**Run:** `portsync.yml`, run 38890845346, commit `d26ea7d`: **54 tests, 0 failed**
(42 in Phase 4, §24, plus the contract and SLA tests below).

- **Consumer contract:** the adapter's real `CoreApi` (strict schemas on) generates a pact of
  **7 interactions** against Pact's mock server; `PortSyncProviderPactTest` verifies it
  against the running PortSync core. Only interactions that hold on any running PortSync
  are in it, so no provider states are needed (DESIGN D59).
- **SLA scenarios** (`SlaScenariosTest`, isolated because they change the whole terminal):
  a gate surge raises `YARD_BLOCK_UTILISATION`, a vessel surge raises `VESSEL_BERTH_WAIT`,
  the SSE stream delivers `event` and `dlq` messages, and with the simulation paused the
  core's yard equals the simulator's block by block. The first version subscribed to
  `/api/events/stream` and got 404; PortSync serves the stream at `/api/stream`.
- **Two real accessibility defects in PortSync**, both colour contrast in its light theme and
  both visible only in some terminal states:
  1. once warning alerts are open, `.tone-warn` text (`--signal` `#b06c00`) on the white panel
     is **4.21:1**, below WCAG AA's 4.5:1 (3.74:1 on `#eef2f5`) — run 38889245065, `22743d4`,
     2026-10-16. Computed fix: `#965b00`, 5.54:1 and 4.92:1.
  2. once a berthed vessel is almost fully worked, its code in the quay schematic
     (`.hull-label .code.muted`, `#536a7c`) sits on the progress fill (35 % of `--ok` over
     `--raised` = `#9dc6c2`) at **3.03:1** — run 39422919521, 2026-10-20. Computed fix (from
     PortSync's `styles.css` at `f34ac31`): `--ink` for that code, 8.90:1 on the fill.
- **Recording them as known issues took three fixes to the harness**, each found by a failing
  PortSync run. This repository cannot change PortSync, so `a11y.known-issues` (`rule@selector`)
  reports a listed violation without counting it, while any other element still fails.
  1. Exact matching broke as soon as several elements failed: axe then names them
     `tr:nth-child(1) > td:nth-child(1) > .tone-warn.pill` (run 39406598844). A listed selector
     now matches by the element's own classes, and as a descendant chain for the second defect.
  2. Failure messages showed the first three elements, all listed ones, hiding the unlisted one
     (run 39425329400); unlisted elements are now named first.
  3. **The match had never worked in CI.** The scanner stored axe's node target with
     `List.toString()`, so every target read `[.tone-warn]` (run 39428100885 showed it once the
     message named the element). The green PortSync runs in between (38890845346,
     39408840903) passed because the defect's page state did not occur in them, not because
     the mechanism worked. The unit tests had used plain selectors; one now feeds axe's real
     list shape (`52112b4`).
  **First run in which it worked:** 39431122556 (`9b26d27`): axe reported one serious
  colour-contrast violation on the overview and one on the alerts page, both counted as known,
  and the suite passed — **54 tests, 0 failed**. (In 39429169337, `52112b4`, neither state
  occurred: 0 violations.)
- **Fixed in PortSync** (PortSync `1bd2080`, release 1.1.0): light `--signal` `#965b00`
  (5.54:1), labels over the quay fill in `--ink` (8.90:1), and a third defect found by auditing
  every colour pair there — the primary button's text on the light amber was 4.41:1, now its own
  `--on-signal` token. PortSync gained a contrast test over all its theme blocks that fails on
  the old palette. This repository's PortSync config lists **no known issues** any more; run
  39444742884 (`f5b0b8a`) scanned with the gate fully strict: 0 violations, 54 tests, 0 failed.

### 37. TeamCity — the real settings on a real server and agent

**Method:** `teamcity.yml` starts TeamCity 2025.07 and an agent built from
`docker/teamcity-agent.Dockerfile` (JDK 21, Docker, Chrome), creates the project over REST
and enables versioned settings in Kotlin, so the TeamCity server itself imports and compiles
`.teamcity/settings.kts` from the branch. `tools/teamcity_run.py` triggers the chain and
reads the results back over REST. Raw results: branches `teamcity/<run id>`.

| Run | Commit | Imported build types | Builds that ran | Result |
|---|---|---:|---|---|
| 38886217452 | `bfd85da` | 16 | settings import and compile only | success |
| 38889245239 | `22743d4` | 16 | Build 349 tests · API 112 · BDD 24 (23 passed, 1 ignored) · Perf · Mutation 69 · UI Chrome 15 + 14 | UI: **5 visual failures**, everything else passed |
| 38890845376 | `d26ea7d` | 16 | Build 350 · API 112 · BDD 24 · Perf · Mutation 69 · UI 15 + 14 | the same 5 visual failures |
| 39406598779 | `88fd713` | 16 | Build 350 · API 112 · BDD 24 (1 ignored) · Perf · Mutation 69 · UI Chrome 15 + 14 (5 visual checks ignored: no baselines for the agent's platform) | **success — every build in the chain green**, 17 min 27 s |

The visual failures (0.67 % to 1.69 % of pixels against a 0.2 % threshold) were real
differences: the agent container renders fonts differently from the GitHub runner that
produced the baselines. Visual baselines are per platform, so each agent now declares its
platform (`visual.platform.suffix`) and, without baselines for it, writes candidates instead
of failing — the same rule as Windows (§34).

### 38. Repeated experiments — how stable the Phase 3 numbers are

Each experiment was run three times. A single run is not a measurement of a noisy quantity.

**Fault seeding** — CI runs 38726144795, 38869649371, 38875469031
([repeats.md](experiments/fault-seeding/repeats.md)). Detection per suite, bugs caught of 6,
per run: contract 2/2/2 · api 6/6/6 · **security 2/1/1** · ui 2/2/2 · a11y 0/0/0 ·
visual 0/0/0 · bdd 5/5/5 · **perf 0/1/1**. Two verdicts changed between runs, both for
`CHECK_DIGIT_EDGE_CASE`, the bug that depends on random data: the security suite caught it
in 1 of 3 runs, and Gatling in 2 of 3 — not through a failed test but through its error-rate
gate, when generated container numbers hit the broken remainder-10 case. So §17's "Gatling
caught nothing" held for that run only. Every baseline was green in every run, and every
other cell gave the same verdict three times.

**Flaky-test demo** — runs 38730129263, 38869660669, 38869821887
([flaky-demo](experiments/flaky-demo/README.md#repeated-3-runs)). The flaky test failed
8, 9 and 5 times in 20 runs (scores 0.368, 0.474, 0.421) and was quarantined every time; the
control scored 0 every time.

**Test impact analysis** — sandbox, three recordings
([repeats](experiments/tia/repeats/README.md)). **18 of 18** selected runs caught their bug.
Wall-clock saved ranged **3.8 % to 33.2 %**, and the same bug moved by up to 16 points between
runs. The map itself differs between recordings of the same code (83 vs 81 tests selected
for `CapacityPolicy.java`), because tests with random data take different paths — which is
why CI re-records it on every push to `main`.

### 39. CI history, the published site and duration-balanced shards

**History and site.** The first `publish` job on `main` (run 39410820490) created the
`ci-history` branch — each pipeline's JSON Lines history per job, `shard-durations.json` (186
tests), the TIA map (138 tests × 47 SUT files), the flakiness leaderboard and a quarantine
proposal — and force-pushed the site (dashboard, Allure report, blog) to `gh-pages`. Turning
on GitHub Pages is a repository setting that a workflow token cannot change, so the site is
served once the owner selects the `gh-pages` branch.

**A bug the re-run exposed.** "Re-run failed jobs" keeps the passed jobs' artifacts, so the
second publish stored 11 history files a second time under the attempt-2 directory: the same
results twice, which doubles that run's durations and adds same-commit "runs" to flakiness.
`dedupe_attempts.py` now drops files a later attempt copied unchanged (tested, idempotent; it
also cleans the copy already on the branch at the next publish).

**Duration-balanced shards.** Pull-request pipelines read the durations recorded on `main`.
Balance is measured as |Σ shard 1 − Σ shard 2| / (Σ shard 1 + Σ shard 2) of the per-shard sum
of test time, per browser; job time is the shard job's wall-clock.

| Run | Split | Chrome | Edge | Firefox | Longest shard job per browser (s) |
|---|---|---:|---:|---:|---|
| 39408840965 (`dc4c3e7`) | hash, 15 + 14 | 7.9 % | 26.8 % | 2.6 % | 108 · 132 · 128 |
| 39410820490 (`768ee4d`) | hash, 15 + 14 | 8.2 % | 13.9 % | 14.5 % | 99 · 126 · 125 |
| 39419767821 (`f5fa6a1`) | durations, 12 + 17 | 0.7 % | 2.0 % | 14.4 % | 92 · 138 · 158 |
| 39422919285 (`3454758`) | durations, 12 + 17 | 6.3 % | 8.9 % | 11.6 % | 96 · 174 · 132 |
| 39423058413 (`b9e4d1f`) | durations, 12 + 17 | 10.4 % | 6.7 % | 18.0 % | 105 · 137 · 105 |
| 39425545521 (`989fa9f`, main) | durations, 12 + 17 | 17.7 % | 6.9 % | 0.6 % | 101 · 119 · 132 |

- The plan does what it was built to do — **the split is stable and every test runs exactly
  once** (12 + 17 = 29 in every browser) — and the mean imbalance fell from **12.3 %** (hash,
  6 shard pairs) to **8.7 %** (durations, 12 pairs).
- **It did not shorten the pipeline.** The longest UI shard job averaged 120 s with the hash and
  124 s with durations. Run-to-run noise on shared runners (the same shard's Σ varies by tens of
  seconds) is as large as what the plan corrects, and the durations come from one recorded run
  averaged over three browsers, while Firefox and Edge run the same tests about twice as long
  as Chrome. Durations per browser and more history are the next steps; the claim stays "more
  even", not "faster".

### 40. An intermittent visual failure in Firefox — `cancel-dialog`

**Observed:** the Firefox `cancel-dialog` check failed in five CI runs on 2026-10-20 and passed
in the others, while five consecutive local Firefox runs (and three full UI-suite runs under
load) differed by **0 pixels**.

| Run | Commit | Mismatch | Changed area (from the failure message, 440×200 capture) |
|---|---|---:|---|
| 39410820490 | `768ee4d` | 2.389 % | — (message added later) |
| 39419767821 | `f5fa6a1` | 0.552 % | — |
| 39425329315 | `da81231` | 0.552 % | — |
| 39429169339 | `52112b4` | 0.567 % | x=90..415, y=31..137: heading row and button row |
| 39444742951 | `f5b0b8a` | 0.306 % | x=90..221, y=31..45: the heading only, from just after "Cancel " |

The diff images stay in CI artifacts, which this environment cannot download, so each step
below was an explanation tested against the next runs:

1. *The capture raced the dialog's focus ring* (it focuses its button in an effect after
   paint). The test now waits for that focus (`3454758`) — the failure came back.
2. *The mask over the per-run message was off by a pixel* (element rects are truncated to whole
   pixels). Masks got a 2-pixel margin (`045271f`) — it came back.
3. *The browser repainted after the page looked ready.* Captures are now retaken until two in a
   row are identical, and each UI job reports when that took more than two (`9b26d27`,
   `f82f93a`). In the failing run on `f5b0b8a` no capture needed retaking: the image was
   stable, just different. Rejected.
4. **What the changed area shows:** the differing pixels are the heading's glyphs *after its
   first word*, and in one run the button labels' — text drawn at a different sub-pixel offset
   from run to run, which changes every anti-aliased edge pixel and nothing else. That is what
   anti-aliasing detection exists for, and it was the simplification D26 had recorded: the diff
   now discounts anti-aliased edge pixels with pixelmatch's detector, reports them separately,
   and still fails on moved edges and changed colours (`ImageDiffTest`, DESIGN D65).

**Status (Phase 7): resolved — the cause was a runner image update from Firefox 157 to 158,
not any of the explanations below; see §46.** What follows is the Phase 6 record, kept as it
was written.

**Status in Phase 6: open, with the likely cause addressed.** Since D65 (`e29d4ac`, run 39449323307;
`d303237`, run 39452850062) the check passed; in the second run, which already reports the
counts, every visual check in every browser matched with **0 differing and 0 anti-aliased
pixels** — the alternate rendering simply did not occur, so D65 has not yet met it in CI. Its
unit tests show the case it targets (an edge drawn half a pixel further) is discounted while a
moved edge or a recoloured area still fails. Each UI job now annotates, per check, the
differing and the discounted pixels, so the next occurrence will show whether it is enough.
### 41. TestHarness on CIForge inside GitHub Actions — `ciforge.yml`

**Method and both run records:** [experiments/ciforge](experiments/ciforge/README.md#in-github-actions--ciforgeyml-2026-10-20).
CIForge is installed from its repository on the runner and runs `.ciforge.yml` against the
pushed commit; the quality gate runs in CIForge's Docker runner (`python:3.12-slim`).

| Actions run | Commit | Result | Wall-clock |
|---|---|---|---|
| 39410844981 | `768ee4d` | **failure** — both UI shards: no `chrome-linux` visual baselines, and with `HARNESS_ENV=ci` a missing baseline fails; the gate was cancelled | — |
| 39419521879 | `f5fa6a1` | **success, 9 of 9 builds**: API 112, BDD 24, UI 15 + 14 with the visual checks run and passed, PIT 143/143, Gatling, quarantine, gate | **5 min 25 s** |

### 42. Capacity on fixed resources — run 39419512861, commit `f5fa6a1`

**Method and raw data:** [experiments/capacity](experiments/capacity/README.md). The SUT runs
in Compose with 2 CPUs / 1 GiB; an open arrival rate of booking flows is stepped up (60 s per
level) on three runners per flow. A level keeps up when errors < 1 %, p95 ≤ 800 ms and
completed flows ≥ 95 % of offered.

| Flow | Capacity, runner 1 / 2 / 3 (flows/s) | What breaks first |
|---|---|---|
| full — sign in on every flow | **25 / 30 / 20** (median 25) | latency: p95 3.2–7.3 s at the next level, 0 % errors; app CPU 162–166 % of its 200 % |
| authenticated — token reused | **200 / 200 / 200** | at 240/s requests time out (p95 60 s, 29.5–35.5 % errors) while the app uses ~97 % CPU: the 20-connection pool had ~5,600 requests waiting |

- **Signing in on every flow costs 8× the capacity** (25 vs 200 flows/s): the password check
  is the work, and the app's CPU rises with the rate until it saturates.
- **The authenticated flow's limit is the connection pool, not CPU.** The app's log at the end
  of the 240/s level, in all three runs: `HikariPool-1 - Connection is not available ...
  (total=20, active=20, idle=0, waiting≈5,600)`.
- **Runners disagreed by up to 50 %** on the CPU-bound flow (20 to 30) with the same limits and
  code — one runner's number alone would have been misleading.
- The first run (39410838330) is discarded: its parser read Gatling's "1,500" as 0 requests, so
  every level failed. Found because a capacity of 0 flows/s at 5 flows/s with 0 errors could not
  be true; fixed with a test (`tools/tests`), and the same latent bug fixed in the CI Gatling
  summary.

### 43. The AI drafter against real models — `ai-eval.yml`

**Method and raw data:** [experiments/ai](experiments/ai/README.md). Three open models served by
Ollama on the runner (CPU only), eight user stories drafted twice each; every drafted step is
matched against the BDD suite's 28 real step definitions the way Cucumber matches.

| Model (run) | Valid Gherkin | Step reuse | Fully runnable drafts | Median s / draft |
|---|---:|---:|---:|---:|
| qwen2.5 1.5B (39419517177) | 14 / 16 | **43.3 %** | 0 | 24 |
| llama3.2 3B (39419517177) | 11 / 16 | **36.3 %** | 0 | 56 |
| qwen2.5 7B (39438329586) | 16 / 16 | **68.5 %** | 1 | 96 |
| qwen2.5 7B (39430576335, summary only) | 15 / 16 | **69.9 %** | 1 | 118 |

- The 7B model reuses existing steps about twice as often as the smaller ones, and two runs of
  it agree within 1.4 points; still only one draft per run runs without new glue code. Most
  misses are near-misses of existing step texts.
- Asked to explain a seeded capacity bug, the 1.5B and 3B models mostly blamed the tests or the
  environment; the 7B model pointed at the API's conflict handling for two of three failures
  but misquoted a status code. The tool labels drafts and explanations as unverified.
- Two runs went wrong and are kept as such: the first 7B job hid its evaluator's failure behind
  `tee` (now `pipefail`), and run 39430576335's results push failed twice without a message (now
  logged; the following run pushed normally).

### 44. Test impact analysis on a real pull request — PR #7

PR #7 changes only Javadoc in `BookingAccessPolicy.java`, a file the map on `ci-history` knows.
The `impacted` job (run 39423058413) selected **53 test cases** — 36 API, 10 BDD, 7 UI, the
same 53 the sandbox experiment selected for that file (§38) — and ran them in a **2 min 7 s**
job, 0 failed. PR #6, which changes workflows and tools, was correctly treated as unscopable:
"run all", nothing early.

**But "first" bought nothing here.** The full API (1 min 19 s) and BDD (1 min 24 s) jobs started
at the same moment on their own runners and finished *before* the impacted subset, and the
Chrome UI shards finished within 30 s of it. With every suite already parallel and about two
minutes long, a subset run first does not give earlier feedback; it would on a serial pipeline
or with much longer suites. The job stays — it is cheap and shows what a change touches — but
it is not a speed-up in this pipeline.

### What could not run in Phase 6, or is still open

- **The diff images of CI visual failures** cannot be downloaded into the authoring
  environment (artifact downloads are blocked there), which is why the Firefox `cancel-dialog`
  failure (§40) is diagnosed from its message, not its picture. *(Phase 7: resolved with the
  images committed by the stability workflow, §46.)*
- **GitHub Pages** cannot be switched on by a workflow token; the owner switched it on and the
  site is live at <https://raktimchandra.github.io/TestHarness/>.
- **`gate-api`'s Testcontainers tests on Windows** — hosted Windows runners cannot run Linux
  containers (§34). *(Phase 7: the same tests now run there on the runner's PostgreSQL, §45.)*
- **A hosted frontier model** for the AI evaluation — it uses small open models through Ollama
  on the runner, so it needs no key and costs nothing, and says nothing about larger models (§43).
- **Repository settings** (branch rules, the dependency graph, Actions variables) cannot be
  changed from the authoring environment; they are set by the owner (README, "Repository
  settings").

## Phase 7

The four items Phase 6 left open: the Firefox visual failure, what Windows did not run, an AI
evaluation limited to small models with one prompt, and CIForge's Docker runner test.

### 45. Windows — now everything the Linux build runs, except Docker itself

**Method:** `windows.yml` on `windows-2025`. New since §34: the SUT's 26 integration tests run
against the runner's PostgreSQL, one fresh database per test class (`-Dgate.it.jdbc-url` with a
`{db}` placeholder, DESIGN D67); the BDD step runs the whole suite instead of `@smoke`; and the
five visual checks compare in Chrome and Edge against Windows baselines (`chrome-windows-ci`,
`edge-windows-ci`) rendered on the same image by `visual-baselines.yml` (run 39474299608) and
reviewed before they were committed. A missing baseline now fails the job.

| Run | Commit | Result | JVM tests |
|---|---|---|---|
| 39468630984 | `fc866d6` | success | gate-api integration tests and full BDD on Windows; 528 tests, 0 failed, 6 skipped (the 5 visual checks, still without baselines, and the drafts report) |
| 39476012939 | `0718151` | success, 9 min 7 s | 530 tests, 0 failed, 1 skipped — the UI suite counted once (below) |
| 39485215657 | `a31f05b` | success, 7 min 39 s | **560 tests, 0 failed, 1 skipped**: `DraftStepCoverageTest.reportOnDrafts`, which needs `-Dai.drafts` on every platform |

Per module in run 39485215657: gate-api 26, gate-domain 69, api-suite 112, bdd-suite 25
(+1 skipped), ui-suite 58 (29 in Chrome, 29 in Edge, the five visual checks in each),
harness modules 270. In run 39476012939 both UI runs passed but the Edge run rewrote the Chrome
run's report files, so the summary counted 29; the job now keeps the Chrome reports apart. The one thing Windows still does not run is anything that needs Docker:
Testcontainers, Compose and the Grid — hosted Windows runners cannot run Linux containers, and
the replacement (the installed PostgreSQL) is what a Windows developer without Docker uses too
(`docs/WINDOWS.md`, troubleshooting).

### 46. The Firefox `cancel-dialog` failure — found and fixed

**Method:** `visual-stability.yml` (new): 30 fresh runners per run, the whole UI suite ten times
per browser, recording per check the differing and anti-aliased pixels, where the element was
drawn to the sub-pixel, the runner image, CPU, browser and font versions, and the failing
images. Data and tables: [experiments/visual-stability](experiments/visual-stability/README.md).

| Run | Commit | Firefox suites with a visual failure | Chrome | Edge |
|---|---|---:|---:|---:|
| 39474301499 | `a4ab182` | **5 / 10** — cancel-dialog, 876 px each | 0 / 10 | 0 / 10 |
| 39475856277 | `123babd` | **4 / 10** — cancel-dialog, 164 px each | 0 / 10 | 0 / 10 |
| 39478245282 | `46ea379` (pinned browsers) | **0 / 10** | 0 / 10 | 0 / 10 |

- **Two renderings, decided by the runner image.** The failing runs laid the dialog out
  200.4 px tall instead of 200.38 px; the diff images show the heading one pixel lower. The
  second run recorded why: GitHub was rolling out image 20261018 (Firefox **158.0**) next to
  20261012 (Firefox **157.0**), and every Firefox run on the new image failed (4 / 4) while every
  run on the old one passed (6 / 6), on any CPU. The baseline had been rendered with 157. This
  explains §40: failures appeared on the day the new image did and came and went with which
  image a job landed on. It was not a repaint (no capture ever needed retaking) and not the
  half-pixel drift D65 was built for.
- **Fix:** the Linux CI profile pins Chrome 155.0.8059.84, Firefox 158.0 and Edge 155.0.4283.62
  (`ui.version.*`, DESIGN D69); Selenium Manager downloads the pinned version where the image
  has another. The baselines were re-rendered with the pins (run 39477608857) and reviewed
  pixel by pixel. In run 39478245282, **30 of 30 suites were green and every check in every
  browser matched with 0 differing and 0 anti-aliased pixels**, on both images (runners with
  Firefox 157 installed downloaded 158).
- **Found on the way, and fixed** (neither caused the failure): Firefox kept `:hover` on a
  clicked input because a single pointer move to (0, 0) sends no event (DESIGN D71; the
  framework's browser tests now run in Firefox and Edge in CI, not only Chrome); and a
  translucent overlay's corners showed the page behind it, which depends on the scroll
  position — up to 95 raw pixels in Firefox locally — so captures now hide the page around the
  element (D70).

### 47. The AI drafter with the suite's step definitions, and a 14B model — run 39464782994, commit `6f681c9`

**Method:** as §43, plus qwen2.5 14B, and each model drafted twice in the same run: with the
BDD suite's 28 step definitions in the prompt (**catalogue**, now the default, DESIGN D68) and
with only phrases of existing features (**phrases**, the earlier prompt). Every draft that hit
the 15-minute request timeout counts as a failure. Data: [experiments/ai](experiments/ai/README.md).

| Model | Step reuse, catalogue | Step reuse, phrases | Valid Gherkin (cat. / phr.) | Fully runnable (cat. / phr.) | Timeouts (cat. / phr.) |
|---|---:|---:|---:|---:|---:|
| qwen2.5 1.5B | **68.3 %** | 27.1 % | 14 / 9 of 16 | 0 / 0 | 2 / 5 |
| llama3.2 3B | 60.1 % | 44.8 % | 6 / 13 of 16 | 0 / 0 | **10** / 2 |
| qwen2.5 7B | **77.7 %** | 57.9 % | 16 / 15 of 16 | 1 / 1 | 0 / 0 |
| qwen2.5 14B | **73.4 %** | 58.8 % | 15 / 15 of 16 | 2 / 0 | 0 / 0 |

- Offering the step definitions raised reuse for every model, by 15 to 41 points; the 7B model
  with them is the best result measured (77.7 %, all 16 drafts valid). 14B did not beat 7B on
  reuse and took about twice as long per draft (269 s median against 141 s).
- The longer prompt made llama3.2 3B time out on ten of sixteen drafts on the 4-vCPU runner.
- The 7B model's phrases-prompt reuse was 57.9 % here against 68.5 % and 69.9 % in §43's runs,
  with one sentence of the system prompt changed in between; that is why the comparison is
  made within one run.
- Explaining the seeded capacity bug, 7B and 14B both called all three failures a product bug
  in capacity enforcement (correct); 1.5B called them test bugs; 3B split them.
- The run's results push failed with exit 141 (SIGPIPE from `git status | head` under
  `pipefail`) — the cause of §43's two lost pushes too. Fixed; the results were collected from
  the run's artifacts by run 39489821085.

### 48. PortSync: a race in the simulator's pause, found by the core/simulator check

`coreAndSimulatorAgreeOnTheYard` pauses the simulator and compares its yard with the core's,
block by block. It failed twice (runs 39464783586 and 39478295663) with the core **one
container ahead** of the yard the test had read after the pause — in the second run for the
full 60 s it waited. PortSync's `SimulationRunner` checked "paused?" and then ran the tick
without a lock, so a tick that had checked just before a pause still changed the yard and
published events after the pause was acknowledged. Fixed in PortSync (PR #1: the tick and
`pause()` share the simulation's lock; `SimulationRunnerTest` holds a slow tick and checks
that `pause()` returns only after it). The test now reads both yards on every poll and passes
when they agree twice in a row with nothing processed in between, so it no longer depends on
when the snapshot was taken, and a real disagreement still fails with both yards.

### 49. CIForge's Docker runner test

The one CIForge test that never ran in the authoring sandbox (`test_docker_runner_executes_
inside_the_image_with_the_workspace_mounted`) needs an image; Docker Hub is blocked there.
With `CIFORGE_TEST_IMAGE=postgres:16-alpine` (an image the sandbox already had) it ran:
**38 passed, 0 failed, 0 skipped**, 92 % coverage (CIForge RESULTS, 1.1.0). In CIForge's own CI
(run 39465064968, Python 3.12 and 3.13) the same 38 passed with `alpine:3.20`, 0 skipped.

### 50. One-command build, 1.2.0 — sandbox, 2026-10-20

`./mvnw clean install` on the 2-vCPU sandbox (Docker for Testcontainers; Chrome 154 for the UI
suites, whose visual checks skip outside CI by design): **540 JVM tests, 0 failed, 6 skipped**
(the 5 visual checks and the AI drafts report), 32 Vitest tests and the Gatling smoke
simulation, in **6 min 36 s** (20:03:05 → 20:09:41 UTC). New since §30: the overlay isolation
browser test, the geometry message and version pin tests, and `th -V`, which printed 1.0.0
through 1.1.0 because the version was a constant; it now comes from the jar manifest.

### What could not run in Phase 7, or is still open

- **Docker on the Windows runner** — Testcontainers, Compose and the Grid run on Linux only
  (§45).
- **Windows browsers are not pinned** — the Windows job uses the runner's own Chrome and Edge,
  which its baselines were rendered with; a Windows image update can need new Windows
  baselines (`visual-baselines.yml`, platform `windows`).
- **The diff images of a failed CI check** still cannot be downloaded here; the stability
  workflow now commits them to its results branch instead (§46).
- **A hosted frontier model** in the AI evaluation — the workflow supports one through the
  Anthropic API (`hosted_model`), but it needs an `ANTHROPIC_API_KEY` repository secret, which
  costs money and is the owner's decision; not run. Open models up to 14B were (§47).
