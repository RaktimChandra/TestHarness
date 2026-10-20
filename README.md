# TestHarness

**A test automation framework that proves its own value.** TestHarness ships with a
realistic system under test — a container-terminal *Gate Appointment* app — that contains
six documented, switchable defects. API, UI, BDD, contract, accessibility, visual,
security and performance tests run against it in one command, and a fault-seeding
experiment measures which suite catches which defect.

Java 21 · Selenium 4 + Grid · REST Assured · Cucumber 7 · JUnit 5 (parallel) · AssertJ ·
Testcontainers · Allure · axe-core · Pact · Gatling · OSV-Scanner · OWASP ZAP · Spring Boot 3 ·
PostgreSQL · React · GitHub Actions · TeamCity

[![ci](https://github.com/RaktimChandra/TestHarness/actions/workflows/ci.yml/badge.svg)](https://github.com/RaktimChandra/TestHarness/actions/workflows/ci.yml)
[![security](https://github.com/RaktimChandra/TestHarness/actions/workflows/security.yml/badge.svg)](https://github.com/RaktimChandra/TestHarness/actions/workflows/security.yml)
[![windows](https://github.com/RaktimChandra/TestHarness/actions/workflows/windows.yml/badge.svg)](https://github.com/RaktimChandra/TestHarness/actions/workflows/windows.yml)

**Live quality dashboard:** [raktimchandra.github.io/TestHarness](https://raktimchandra.github.io/TestHarness/)
— rebuilt from the recorded CI history after every pipeline on `main`.

---

## Results (measured)

From real runs on 2026-10-14 to 2026-10-20 — method, machine and raw numbers for every row are in
[RESULTS.md](RESULTS.md). Nothing in this table is estimated.

| | |
|---|---|
| Tests in one `verify` | **526 JVM tests + 32 Vitest + 1 Gatling simulation**, 0 failed (5 visual checks skip outside CI by design); dashboard: 9 more Vitest |
| Wall-clock, clean build + everything (2 vCPU sandbox) | **6 min 32 s** |
| Fault seeding: 6 bugs × 8 suite types (CI), **run three times** | **6/6 caught** by at least one suite in every run · API 6/6/6 · BDD 5/5/5 · contract, UI 2/2/2 · security 2/1/1 · Gatling 0/1/1 · the race bug caught by **one test of 151** · 46 of 48 cells gave the same verdict all three times |
| Mutation score, domain rules (PIT, STRONGER) | **95.1 % → 100 %** (143 mutants) after turning the 7 survivors into tests |
| Test impact analysis, per-test JaCoCo, three recordings | **18/18** selected runs still caught their bug; wall-clock saved **3.8–33.2 %** (and why so little) |
| Flaky-test detection (20 runs on one commit, three times, CI) | deliberately flaky test scored **0.368 / 0.474 / 0.421** and was quarantined every time; its stable control scored 0 every time |
| A second, unrelated system (PortSync: Kafka + Spring + React) | **54 tests, 0 failures** in CI with the framework unchanged, including a consumer contract and SLA scenarios; found **two real WCAG contrast defects in PortSync** (4.21:1 and 3.03:1), now fixed there |
| Dependency vulnerabilities (OSV-Scanner over a Maven-resolved SBOM) | **41 advisories in 18 transitive packages** on the first scan → 0 untriaged after version floors; 3 Spring 6.2 advisories ignored with code-checked reasons and an expiry date |
| OWASP ZAP baseline of the running SUT | **8 alert types** on the first scan → 6 header findings fixed in the app, 3 informational rules triaged; now a gate |
| Windows (`windows-2025`, the Windows guide's own commands) | **500 tests, 0 failed** with `mvnw.cmd` / `th.cmd`, UI in Chrome and Edge; found 4 problems Linux CI could not |
| TeamCity 2025.07, real server + agent | the server imports and compiles `settings.kts` and runs the chain: **every build green** (build 350, API 112, BDD 24, UI 15 + 14, Gatling, PIT) |
| CI pipeline (`main`, run 39425545521) | **quality gate green in 13 min 18 s**, 22 jobs: UI sharded 3 browsers × 2 by recorded duration, quarantine non-blocking (its flaky test failed, as designed), then history and the dashboard published in 3 min 36 s more; the same chain on CIForge in 5 min 25 s |
| Capacity on 2 CPUs / 1 GiB (three runners per flow) | **25 booking flows/s** with sign-in on every flow (CPU-bound), **200** with a reused token (then the 20-connection pool runs out) |
| AI drafter on real open models (8 stories × 2, Ollama in CI) | qwen2.5 7B: **100 % valid Gherkin, 68.5 % of drafted steps run on existing step definitions** (69.9 % in a second run); 1.5B / 3B: 43 % / 36 %; one draft per 7B run needed no new glue |
| Self-healing locators | **1 heal in 26 UI tests** — the demo that renames a `data-testid` — reported with the exact fix, never silent |
| Cross-browser | UI suite passes in **Chrome 154, Firefox 157 and Edge 154** in CI, both directly and on **Selenium Grid** |
| Accessibility (axe-core, WCAG 2.0/2.1 A+AA) | **0 violations** on 9 page states × 3 browsers |
| Contract (Pact) | **9/9** interactions from the UI's real client verified against the real API |
| Booking flow under load (60 users, CI runner) | **p95 73 ms · p99 78 ms · 0 errors** (gates: 800 / 1500 ms / 1 %) |
| Grid scaling, 1 → 4 workers on one 4-vCPU runner | test span **−13 to −23 %**; 8 workers slower than 4 — CPU-bound, explained in RESULTS §8 |
| Real defects found while building the suites | **8** — 3 in the SUT, 5 in tests/framework/infra, each with the run or test that found it |
| Domain rules coverage (own unit tests) | 98.4 % lines · 100 % branches |

## What makes it different

- **A SUT you can break on purpose.** Six seeded bugs (off-by-one capacity, ISO 6346
  check-digit edge case, timezone shift, race on the last place, missing authorisation,
  UI-only validation) are runtime switches beside the correct code — the basis for
  measuring defect detection rather than claiming it.
- **Contract checks on every response.** A REST Assured filter validates each response
  against a JSON Schema (draft 2020-12, formats asserted). Unregistered endpoints fail in
  strict mode, and every error on every path must be an RFC 9457 problem with a stable code.
- **Concurrency tested for real.** Six drivers race for one place over HTTP; exactly one
  wins. With the race bug switched on, more than one does (`GateApiIntegrationTest`).
- **Parallel by construction.** One browser per thread, and every test creates its own users
  and its own lane of slots, so nothing is shared between tests.
- **No sleeps, enforced.** ArchUnit fails the build on `Thread.sleep`; waits are explicit and
  describe what they waited for. Retries exist only for infrastructure and are bounded.
- **Evidence for every failure.** Screenshot, page HTML, URL and browser console (WebDriver
  BiDi, so the same in Chrome, Firefox and Edge), plus request/response bodies.
- **Independent oracles.** The test-side ISO 6346 validator shares no code with the SUT, so
  a check-digit bug can't be reproduced by the tests meant to catch it.
- **Honest config.** Secrets come only from environment variables; in CI there is no
  fallback — the first pipeline run failed precisely because one was missing.
- **Contracts generated by the real client.** The Pact consumer test drives the UI's own
  API client; the contract is committed and CI fails if it drifts. Provider verification
  found a real timestamp-format bug.
- **Visual checks that don't cry wolf.** Perceptual diff, per-platform baselines rendered in
  CI and approved by merging a branch, and page stabilisation — the two flaky causes found
  (focus rings, neighbouring data) were diagnosed and fixed, not hidden behind a tolerance.
- **Accessibility on every state.** axe-core scans dialogs, errors and panels, not just
  landing pages, and the build fails on critical or serious violations.
- **Defect detection measured per suite.** Each seeded bug is switched on in CI and every
  suite type runs against it; a suite gets credit only if it was green without the bug.
  The result included a strategy mistake (Pact was expected to catch the timezone bug; it
  can't) — kept in the docs, not hidden.
- **Flakiness from history, not from retries.** Every result goes to JSON Lines and
  optionally PostgreSQL; a test that flips on the *same commit* is scored, classified
  (locator / timeout / assertion / environment / data) and quarantined into a non-blocking
  job by a JUnit discovery filter — no `@Disabled` edits, no retry-until-green.
- **Self-healing that tells on itself.** Ranked fallbacks (test id, id, accessible name, text,
  position); a healed step is labelled `healed` in Allure with the exact fix, ambiguous
  matches are never used, and a CI mode turns any heal into a failure.
- **Test impact analysis with its limits measured.** Per-test JaCoCo coverage of the SUT
  maps tests to files; selecting from a diff caught every seeded bug but saved far less
  time than vendors suggest, and RESULTS says why.
- **AI as a drafting tool, never a committer.** Any OpenAI-compatible or Anthropic endpoint
  via an API key; drafts Gherkin from a user story (parsed, banner-marked, `@draft`, written
  outside the source tree) and summarises failed runs. Everything works without it.
- **A quality dashboard that reports only what was measured.** Pass rate, flakiness,
  quarantine, coverage, mutation, accessibility, visual review, the defect detection matrix
  and TIA in one static page built from the files runs leave behind; missing data is listed,
  never filled in. Its first real data exposed a coverage-measurement bug in the framework.
- **Not tied to its demo app.** The same framework drives PortSync, an event-driven Kafka
  system, through a small adapter (`adapters/portsync`) — API, contract, UI, a11y and BDD.
- **One CLI.** `./th run --suite ui --browser firefox --tags smoke`, `./th rerun-failed`,
  `./th report`, `./th history flaky`, `./th tia select`, `./th ai gherkin`.
- **Measured, not claimed, parallel speed-up.** Grid timings at 1/4/8 workers with test
  span and per-test time separated — including the result that more workers made it slower.

## Architecture

```mermaid
flowchart LR
  subgraph SUITES["suites/"]
    SAPI["api-suite<br/>85 + 17 security"]
    SUI["ui-suite<br/>17 journeys · a11y · visual"]
    SBDD["bdd-suite<br/>15 scenarios"]
    SPERF["perf-suite<br/>Gatling"]
    SUP["suite-support<br/>SUT launcher · Gate client<br/>schemas · page objects"]
  end
  subgraph FW["harness/ (SUT-agnostic framework)"]
    HAPI["api"] --- HUI["ui<br/>+ visual · a11y · healing"] --- BDD["bdd"]
    DATA["data"] --- REP["reporting"] --- CORE["core"]
    INS["insights<br/>history · flakiness · quarantine<br/>TIA · AI drafts"] --- CLI["cli (th)"]
  end
  HIST[("run history<br/>JSONL · PostgreSQL")]
  INS -. "records every result" .-> HIST
  subgraph SUT["sut/ (Gate Appointment)"]
    WEB["React UI"] --> API["Spring Boot API"] --> DOM["domain rules<br/>+ seeded bugs"]
    API --> PG[(PostgreSQL)]
  end
  PACT[["contracts/pacts<br/>(Pact)"]]
  GRID["Selenium Grid<br/>Chrome · Firefox · Edge"]
  WEB -. "consumer test writes" .-> PACT
  PACT -. "provider test verifies" .-> API
  SAPI & SUI & SBDD & SPERF --> SUP --> FW
  SUP -- "HTTP / real browser only" --> SUT
  SUP -. "starts in-process with<br/>Testcontainers PostgreSQL" .-> SUT
  SUI -. "optional" .-> GRID -.-> WEB
```

Design decisions and trade-offs: [DESIGN.md](DESIGN.md) · Test pyramid and what each
suite proves: [TEST_STRATEGY.md](TEST_STRATEGY.md) · How to write tests:
[FRAMEWORK_GUIDE.md](FRAMEWORK_GUIDE.md).

## Quick start

Prerequisites: **JDK 21**, **Docker** running, **Chrome**. No Maven or Node install needed.

```bash
./mvnw verify                      # Windows: .\mvnw.cmd verify
```

That builds the SUT and the framework, starts PostgreSQL (Testcontainers) and the app, and
runs the unit, framework, contract, API + security, UI (headless Chrome, with
accessibility and visual checks), BDD and Gatling suites.

```bash
./mvnw -N -Preport verify          # render the Allure report for everything that ran
jwebserver -d "$PWD/target/allure-report" -p 8000   # then open http://localhost:8000
```

Or through the `th` command (Windows: `th.cmd`), after `./mvnw install -DskipTests` once:

```bash
./th run --suite api                      # one suite (api | ui | bdd | perf | all)
./th run --suite ui --browser firefox --tags smoke
./th rerun-failed                         # only what failed last time, per module
./th report                               # generate + open the Allure report
./th history flaky --jsonl suites         # flakiness leaderboard from recorded history
./th dashboard --open                     # the quality dashboard from your last runs
./th --help                               # everything else (tia, ai, history quarantine)
```

Windows, step by step and one command at a time: [docs/WINDOWS.md](docs/WINDOWS.md).
Adding your first test: [docs/NEW_TEST_IN_5_MINUTES.md](docs/NEW_TEST_IN_5_MINUTES.md).

Common variations:

```bash
./mvnw verify -pl suites/api-suite -am                              # one suite
./mvnw verify -pl suites/ui-suite -am -Dharness.ui.browser=firefox  # another browser
./mvnw verify -pl suites/bdd-suite -am -Dcucumber.filter.tags=@smoke
docker compose up -d --build && HARNESS_ENV=docker ./mvnw verify -pl suites/api-suite -am
./mvnw verify -pl suites/perf-suite -am -Dperf.users=200      # heavier load test
./mvnw verify -DskipPerf                                       # everything except Gatling
```

On Selenium Grid (hub + Chrome/Firefox/Edge nodes in Docker):

```bash
docker compose -f docker-compose.yml -f docker-compose.grid.yml up -d --build --wait
HARNESS_ENV=docker ./mvnw verify -pl suites/ui-suite -am -Dharness.ui.grid-url=http://localhost:4444 \
  -Dharness.sut.browser-base-url=http://gate:8080 -Dharness.ui.browser=edge -DexcludedGroups=visual
```

## The system under test

| Role | Can |
|---|---|
| Driver (`driver1`..`driver3`) | book a slot for a container (drop-off or pick-up), see own bookings, reschedule or cancel up to 1 h before the slot |
| Clerk (`clerk1`, `clerk2`) | open slots, search all bookings, check a truck in from 2 h before the slot until 30 min after it ends |

Demo password: `gate-demo-pass` (override with `GATE_SEED_PASSWORD`). Error codes:
[docs/errors.md](docs/errors.md).

| Driver: booking with live ISO 6346 check | Clerk: search and check-in |
|---|---|
| ![Driver page](docs/images/ui-driver.png) | ![Clerk page](docs/images/ui-clerk.png) |

![Allure overview from a real run](docs/images/allure-overview.png)

The quality dashboard (`./th dashboard`), rendered from a real local build plus the
committed 20-run flaky-test demo:

![Quality dashboard](docs/images/dashboard.png)

### Seeded defects

| Bug | What goes wrong |
|---|---|
| `OFF_BY_ONE_CAPACITY` | a slot accepts capacity + 1 bookings |
| `CHECK_DIGIT_EDGE_CASE` | valid containers whose check digit comes from remainder 10 are rejected |
| `TIMEZONE_SLOT_TIMES` | slot times are relabelled instead of converted (shifted by the zone offset) |
| `RACE_ON_LAST_SLOT` | concurrent bookings for the last place all succeed (no row lock) |
| `MISSING_AUTHORISATION_CHECK` | drivers can read and change other drivers' bookings |
| `API_SKIPS_CONTAINER_VALIDATION` | the UI validates container numbers; the API doesn't |

Switch them on for a run with `-Dharness.sut.seeded-bugs=OFF_BY_ONE_CAPACITY,RACE_ON_LAST_SLOT`.

## Screenshots and GIFs for your own runs

- Every failed UI test leaves `suites/*/target/evidence/<test>/screenshot.png`,
  `page.html` and `console.log`; a failed visual check also leaves
  `target/visual/diff/<platform>/<name>-actual.png` and `-diff.png`.
- To capture a journey as a GIF: run a UI test with `-Dharness.ui.headless=false`, record
  the browser window with ScreenToGif (Windows) or Peek (Linux) and save it under
  `docs/images/`.
- The screenshots above were taken from the running app with headless Chrome.

## CI/CD

```mermaid
flowchart LR
  B["build<br/>SUT + framework + Pact"] --> API["API suite"] & BDD["BDD suite"]
  B --> UI["UI suite<br/>Chrome · Firefox · Edge<br/>× 2 shards"]
  B --> CS["Compose smoke"] & G["Selenium Grid"] & P["Gatling"] & M["PIT ≥ 95 %"]
  B --> D["dashboard build check"] & TC["TeamCity DSL compiles"]
  B -.-> Q["quarantined tests<br/>(non-blocking)"]
  B -.-> IMP["PR: impacted tests first<br/>(TIA map from main)"]
  API & BDD & UI & CS & G & P & M & D & TC --> QG{{"quality gate<br/>the one required check"}}
  Q -. reported only .-> QG
  API & BDD & UI & Q --> R["Allure report"]
  QG --> PUB["main: publish<br/>ci-history + gh-pages"]
  PUB -. durations, TIA map .-> UI
```

Alongside it: `security.yml` (OSV-Scanner over an SBOM + OWASP ZAP baseline), `windows.yml`,
`portsync.yml`, `teamcity.yml` (a real TeamCity server and agent), `ciforge.yml` (CIForge
inside Actions), and the experiments `fault-seeding.yml`, `flaky-demo.yml`, `capacity.yml`,
`ai-eval.yml`. Releases are cut by `release.yml`.

The same pipeline exists three times, each labelled with how far it was proven:

| | File | Status |
|---|---|---|
| GitHub Actions | `.github/workflows/ci.yml` | runs on every push and PR; on `main` it also records history and publishes the site (RESULTS §31, §39) |
| CIForge (my TeamCity-style orchestrator) | `.ciforge.yml` | **executed** in the sandbox (8 min 11 s) and inside GitHub Actions (5 min 25 s), 9 of 9 builds green (RESULTS §28, §41) |
| TeamCity | `.teamcity/settings.kts` | **executed**: a TeamCity 2025.07 server imports and compiles the Kotlin settings and runs the chain on an agent, every build green (RESULTS §37) |

### Repository settings the pipeline expects

Set once by the owner (a workflow token cannot change them):

| Setting | Value | Why |
|---|---|---|
| Pages | source `gh-pages`, `/ (root)` | serves the dashboard the `publish` job builds |
| Rules → ruleset on the default branch | pull request required; status check **Quality gate** required and up to date; no force pushes | the gate is the one check that decides a merge |
| Code security → Dependency graph | enabled | needed by the `dependency-review` job |
| Actions variable `DEPENDENCY_REVIEW` | `true` | switches that job on (it fails on new high-severity dependencies in a PR) |

Interview preparation for this project: [INTERVIEW.md](INTERVIEW.md).

## Roadmap

| Phase | Status |
|---|---|
| 1 — SUT, framework core, API/UI/BDD suites, CI, docs | **done** |
| 2 — Selenium Grid in Compose, visual regression, axe-core a11y, Pact, Gatling, security smoke | **done** |
| 3 — fault-seeding experiment, flaky-test intelligence, honest self-healing locators, test impact analysis, PIT, AI-assisted test design | **done** |
| 4 — React quality dashboard, CLI, "new test in 5 minutes" guide, PortSync adapter | **done** |
| 5 — full CI/CD with quality gates, TeamCity Kotlin DSL, CIForge pipeline | **done** |
| 6 — closing the gaps: duration shards, published history and dashboard, TIA on PRs, sign-in throttling, OSV + ZAP, Windows, TeamCity and CIForge executed, AI eval on real models, capacity, repeated experiments | **done** (1.1.0) |

Changes per phase: [CHANGELOG.md](CHANGELOG.md).

## Licence

MIT © 2026 Raktim Chandra
