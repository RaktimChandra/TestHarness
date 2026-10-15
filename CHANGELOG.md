# Changelog

All notable changes, one section per phase. Each phase ends with the whole project
compiling, every test passing, and `./mvnw verify` running end to end.

## [1.0.0-phase3] — 2026-10-15 — Measuring the suites: fault seeding, flakiness, healing, TIA, mutation, AI drafts

### Added
- **Fault-seeding experiment** (`.github/workflows/fault-seeding.yml`, `.github/scripts/fault_*.py`):
  every seeded bug × eight suite types plus a no-bug baseline in CI; a suite is credited
  only against its own green baseline. Results and the detection matrix in
  `experiments/fault-seeding/` (RESULTS §17) and a blog draft in `docs/blog/`.
- **`harness-insights` module:**
  - run history for every JUnit and Cucumber test (`HistoryListener`, auto-registered) to
    JSON Lines and optionally PostgreSQL (`HistoryStore`, idempotent upserts);
  - failure-cause classification (locator, timeout, assertion, environment, data) from the
    exception chain;
  - flakiness score from same-commit flips, a quarantine file, and three JUnit Platform
    discovery filters — quarantine (`off`/`exclude`/`only`), selection from a file, and
    stable CRC32 sharding;
  - test impact analysis: per-test JaCoCo coverage of the in-process SUT (`TiaRecorder`),
    a test → file map, and a conservative selector;
  - provider-agnostic AI drafting (OpenAI-compatible or Anthropic Messages via
    `HARNESS_AI_API_KEY`): Gherkin from a user story, validated with the Gherkin parser,
    banner-marked, `@draft`, written only to `target/ai-drafts/`; failure summaries that work
    without a model.
- **Honest self-healing locators** (`harness-ui` `heal` package): `Target`s with ranked
  fallbacks (data-testid, id, accessible name, text, position below a label); a heal is
  labelled `healed` in Allure with a suggested fix and written to `healing.jsonl`;
  ambiguous matches are never used; `ui.healing=fail` turns any heal into a failure.
  Demo on the real app (`SelfHealingDemoTest`).
- **Deliberately flaky demo** (`suites/flaky-demo`, off by default) and
  `.github/workflows/flaky-demo.yml`: 20 runs on one commit with PostgreSQL history, then
  leaderboard, quarantine and both quarantine modes (RESULTS §20).
- **Mutation testing**: `-Pmutation` runs PIT (STRONGER) on `sut/gate-domain` with a 95 %
  gate; the 7 first-run survivors became `SurvivingMutantsTest` (RESULTS §18). New CI job
  `mutation` with a job summary of the score and any survivors.
- **`th` command** (`harness-cli`, picocli; `./th`, `th.cmd`): `run` (suite, tags, browser,
  environment, Grid, quarantine, shard, selection, seeded bugs, `--dry-run`),
  `rerun-failed`, `report`, `history ingest|flaky|quarantine`, `tia map|select`,
  `ai gherkin|explain`; `-Dname=value` anywhere sets a system property.
- **TIA experiment** (`tools/tia_experiment.py`, `experiments/tia/`, RESULTS §19).

### Fixed — found while building or running Phase 3
- **Framework** (caught by its own tests before `8810cc9` landed): parameterised tests are
  containers at discovery time, so the first filter version let them through quarantine,
  selection and sharding.
- `1565a4e` **Framework**: the selection and shard filters read `harness.select` /
  `harness.shard`, but configuration strips the `harness.` prefix; `-Dharness.select=…`
  silently selected everything. Found by the TIA experiment's first run; regression test
  added.
- `72f79e5` **Framework**: runs with seeded bugs were recorded under the plain commit SHA,
  so the experiments' deliberate failures looked like same-commit flips (the local
  leaderboard showed 20 "flaky" tests). They are now `<sha>+seeded:<bugs>`.
- `95f8686` **Tests**: 7 surviving mutants exposed untested behaviour in the domain rules
  (message text, per-argument duration checks, slot length bounds, bug switches' defaults).
- **CLI**: a directory given to `--jsonl` read every `.jsonl` file, including healing records,
  and failed; only files whose records have a `runId` are read now.
- `d261ce8` **CI**: the dependency-cache workflow ran PIT during the empty-jar install step.

### Measured
458 JVM tests + 31 Vitest + 1 Gatling simulation, 0 failures, 5 min 51 s (RESULTS §16).
Fault seeding 6/6 bugs caught (API 100 %); PIT 95.1 % → 100 %; TIA 6/6 caught with
3.8–21.6 % time saved; flaky demo score 0.368, quarantined.

### Could not be run in the authoring sandbox
- The AI features against a real model provider (no API key); verified against stub servers
  for both wire formats.
- The PostgreSQL-backed flaky demo, Grid and Compose (Docker Hub blocked) — CI only.
- GitHub Actions artifact downloads (blocked) — experiment workflows push their results to
  branches instead, copied unchanged into `experiments/`.

## [1.0.0-phase2] — 2026-10-14 — Grid, visual, accessibility, contracts, performance, security

### Added
- **Selenium Grid in Docker Compose** (`docker-compose.grid.yml`): hub plus Chrome, Firefox
  and Edge nodes (4.51.0); a separate `sut.browser-base-url` so browsers in containers
  reach the SUT by service name; nodes advertise the published hub URL so WebDriver BiDi
  works through the Grid. CI runs the UI suite on the Grid at 1, 4 and 8 workers (two
  repetitions, recording Maven wall-clock, test-execution span and Σ test time) and
  cross-browser on Firefox and Edge.
- **Visual regression** (`harness-ui` `visual` package): perceptual YIQ image diff with a
  mismatch-ratio threshold, ignore-regions, per-platform baselines, candidate/update/
  compare modes, page stabilisation before capture, and baseline/actual/diff images in the
  Allure report. `visual-baselines.yml` renders candidates on the CI image and pushes them
  to a review branch; merging it approves them. Five component checks in the UI suite with
  baselines for Chrome, Firefox and Edge.
- **Accessibility** (`harness-ui` `a11y` package): axe-core WCAG 2.0/2.1 A+AA scanner with a
  per-impact gate (critical 0, serious 0; moderate/minor reported), JSON per page, and a CI
  summary table. Nine page states scanned in every browser.
- **Contract testing with Pact**: a V4 consumer contract generated by the UI's real API
  client (9 interactions, Pact JS in Vitest), committed under `contracts/` with a CI drift
  check, and verified against the real provider and PostgreSQL in `gate-api`.
- **Performance smoke with Gatling** (`suites/perf-suite`): driver booking flow under an open
  ramp with p95/p99/error-rate gates; CI summary parsed from Gatling's console report.
- **Security smoke tests** (17, `@Tag("security")`): forged/unsigned JWTs, IDOR, role
  checks, mass assignment, hostile identifiers, injection and reflection, security headers,
  error hygiene, test-support key.
- Strict schema registry entries can declare a deliberately non-JSON response (`schema: none`).
- CI failure annotations now lead with Maven `[ERROR]` lines; repeated warnings are counted
  instead of flooding them out.

### Fixed — found by the new tests
- `5a394b0` **SUT**: timestamps mixed whole seconds with micro/nanoseconds (found by Pact
  provider verification); all times now have whole-second precision, enforced by schema.
- `c98cd77` **SUT**: URLs rejected by Spring's firewall or Tomcat (`;`, encoded `%`, encoded
  `/`) returned a bare or HTML 400 instead of the problem+json contract (found by the
  security tests).
- `94e5696` **Tests**: two deep-link journeys opened the API URL in the browser — fine
  locally, `ERR_CONNECTION_REFUSED` on Grid (found by the Grid job).
- `f8a001d` **Framework**: focus rings made visual checks depend on window focus (2.0–3.5 %
  differences between CI jobs); captures are now stabilised.
- `bfb55c0` **Tests**: the booking-form visual check depended on how many slots other tests
  created that day (2.76 % in CI); it now uses its own date.
- `0455bcf` **Grid**: BiDi connections failed because nodes advertised the hub's container IP.
- `846dfec` **Framework**: the first four parallel Edge starts in a CI job failed together with
  `SessionNotCreated`; browser start-up now creates the first session per browser alone
  and retries `SessionNotCreated` once (`ui.session-attempts`).

### Could not be run in the authoring sandbox
- Selenium Grid and the Docker Compose stack (Docker Hub is blocked) — both run in CI
  (`grid`, `compose-smoke` jobs).
- Edge (no binary) — runs in CI, locally and on the Grid.
- Visual comparisons against the committed baselines: the baselines are rendered on the CI
  runner image; locally the visual tests write candidates and are skipped (5 skipped in the
  sandbox run). The sandbox verified stability by rendering and comparing its own
  baselines (0 differing pixels in two full parallel runs).
- Artifact and raw-log downloads from GitHub Actions (blocked by the sandbox's egress
  policy) — failures were diagnosed from check-run annotations and local reproduction.

## [1.0.0-phase1] — 2026-10-14 — System under test + core framework

### Added — system under test (`sut/`)
- **gate-domain**: framework-free rules — ISO 6346 container numbers (incl. the
  remainder-10 → check-digit-0 case), slot capacity, booking/cancel cut-off, check-in window,
  status machine, object-level access policy, booking references (Crockford base-32), and
  six runtime-switchable **seeded bugs** with descriptions.
- **gate-api**: Spring Boot 3.5 REST API — HS256 JWT login with DRIVER/CLERK roles; slots,
  bookings, reschedule, cancel, check-in, search; PostgreSQL via Flyway and plain JDBC with
  a `SELECT … FOR UPDATE` slot lock; RFC 9457 problem responses with stable codes; keyed,
  off-by-default test-support API (bug switches, clock offset, throw-away users); OpenAPI.
- **gate-ui**: React 19 single-page app (login, driver booking/reschedule/cancel, clerk
  search/check-in) with `data-testid` on every element, client-side ISO 6346 validation,
  terminal wall-clock time display; packaged into the API jar.
- Docker image (multi-stage) and Compose stack (PostgreSQL + app, health checks, ports bound
  to 127.0.0.1).

### Added — framework (`harness/`)
- **core**: layered config (`harness-defaults.yml` → `harness.yml` → `harness-<env>.yml`
  → `HARNESS_*` → `-Dharness.*`) with value origins; env-only secrets that redact
  themselves and never fall back in CI; JSON structured logging with run/test context;
  bounded `Retry`, `Await`, and ArchUnit rules banning sleeps, stray parking and stdout.
- **reporting**: Allure facade (`Report`), environment/executor/categories files, history
  carry-over between runs.
- **data**: seeded per-thread Faker, collision-free ids, independent ISO 6346 oracle and
  generators, `@JsonCases` / `@CsvRecords` data-driven sources, Testcontainers
  `PostgresFixture` with parameterised JDBC.
- **api**: REST Assured specs with logging, Allure and **JSON Schema validation of every
  response** (draft 2020-12, strict mode, catch-all error rules), token cache, redacted
  credentials, `ResponseAssert`.
- **ui**: Selenium 4 driver factory (local or Grid), ThreadLocal sessions, explicit-wait
  library, `BasePage`/`Component`, locale-independent date input, WebDriver BiDi console
  capture, failure evidence (screenshot, HTML, URL, console), JUnit 5 `BrowserExtension`.
- **bdd**: typed per-scenario context and hooks (logging, `@ui` browser lifecycle, evidence).

### Added — suites (`suites/`)
- **suite-support**: in-process SUT launcher (Testcontainers PostgreSQL) or external target;
  typed Gate client and models; per-test isolated fixtures; JSON Schemas for every Gate
  response; page objects; API-token sign-in for UI journeys.
- **api-suite** (85 tests), **ui-suite** (17 journeys), **bdd-suite** (15 scenarios).

### Added — build, CI and docs
- Multi-module Maven build with Maven Wrapper, pinned plugin versions, JaCoCo, an Allure
  `report` profile that runs the Allure CLI through Java (works on Windows).
- GitHub Actions: build + framework tests, API, BDD, UI × {Chrome, Firefox, Edge}, Compose
  smoke test in external mode, merged Allure report; per-job test summaries.
- Workflows `bootstrap-probe` (dependency versions, runner tooling, wrapper) and
  `dev-cache` (offline build inputs for restricted environments).
- README, DESIGN, TEST_STRATEGY, FRAMEWORK_GUIDE, RESULTS, docs/errors.md, docs/WINDOWS.md.

### Fixed during the phase (found by the framework or by CI)
- `9cc795c` **SUT**: 14 kinds of 4xx responses lacked the error `code` (found by strict
  schema validation).
- `c0598f1` SUT launcher: settings were passed as lowest-precedence defaults, so the app
  ignored the Testcontainers database; a failed start is now reported once and fast.
- CI: strict secret resolution correctly failed the first run (no `GATE_SEED_PASSWORD`);
  CI now generates one per run and the embedded SUT seeds with the same secret.
- `GateApiIntegrationTest` depended on the environment's seed password; it now pins its own.
- Docker image health check used `/dev/tcp` under `sh` (dash); now runs under bash, and the
  Compose job surfaces container logs on failure.

### Could not be run in the authoring sandbox
- Edge (no binary) — ran in CI.
- Docker Compose stack (Docker Hub blocked) — runs in the CI `compose-smoke` job.
- Online Maven resolution / Maven Wrapper download (Maven Central blocked) — the sandbox
  ran Maven offline from a CI-built dependency cache; CI runs online through `./mvnw`.
