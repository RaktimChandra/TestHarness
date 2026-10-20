# Changelog

All notable changes, one section per phase. Each phase ends with the whole project
compiling, every test passing, and `./mvnw verify` running end to end.

## [1.1.0] — 2026-10-20 — Phase 6: closing the gaps

### Added
- **Duration-balanced UI shards** (`ShardPlan`, longest-first onto the lightest shard): every
  shard computes the same plan from the durations recorded on `main`; tests without a
  recorded duration fall back to the hash, so a stale file never loses or duplicates a test.
  `th history durations` writes the file.
- **CI history and a published dashboard:** after every pipeline on `main`, the `publish` job
  appends the run's results to the `ci-history` branch (120-run window), derives shard
  durations, the flakiness leaderboard and a quarantine proposal, and publishes the dashboard,
  the Allure report and the blog to `gh-pages`.
- **Test impact analysis on pull requests:** `tia-map` records per-test coverage on every push
  to `main`; the `impacted` job runs the selected tests first on a PR (feedback, not a gate).
- **Sign-in throttling in the SUT:** 5 failures per username or 100 per client in 15 minutes
  lock that key for 5 minutes (`429 TOO_MANY_ATTEMPTS`, `Retry-After`), tested at unit,
  integration, black-box and UI level.
- **Security workflow:** OSV-Scanner over a Maven-resolved CycloneDX SBOM plus npm lockfiles,
  and an OWASP ZAP baseline of the running SUT; both fail on anything untriaged.
- **Windows workflow** running the commands of `docs/WINDOWS.md` (`mvnw.cmd`, `th.cmd`, the UI
  suite in Chrome and Edge) on `windows-2025`.
- **TeamCity on a real server and agent** (`teamcity.yml`, `docker/teamcity-agent.Dockerfile`,
  `tools/teamcity_run.py`): the server imports and compiles `.teamcity/settings.kts` through
  versioned settings and runs the chain.
- **TestHarness on CIForge inside Actions** (`ciforge.yml`, quality gate in CIForge's Docker
  runner); CIForge 1.1.0 gained API tokens.
- **AI drafter evaluation against real models** (`ai-eval.yml`: Ollama on the runner, 8 user
  stories) scored by `DraftStepCoverage`, which matches drafted steps against the BDD suite's
  real step definitions.
- **Capacity experiment** (`capacity.yml`, `tools/capacity.py`): stepped arrival rate against
  the SUT limited to 2 CPUs and 1 GiB, with and without sign-in on the request path.
- **PortSync consumer contract** (7 interactions, verified against the running core) and SLA
  scenarios (gate surge, vessel surge, SSE, paused-yard agreement).
- **Accessibility known issues** (`a11y.known-issues: rule@selector`) for systems the tests do
  not control: reported, not counted, and any other element still fails.
- **Release workflow** (`release.yml`): tag, notes from this file, source archive, SBOM,
  Allure report and site.
- **Anti-aliasing detection** in the visual diff (pixelmatch's detector): anti-aliased edge
  pixels are counted separately and do not fail a check; moved edges and changed colours do.
- Visual captures are retaken until two consecutive ones agree; each UI job annotates retaken
  captures and the differing / anti-aliased pixels per check.
- Tests for the Python tools and CI scripts (`tools/tests`, run in CI).
- Environment variables reach dashed configuration keys (`HARNESS_DB_PASSWORD` →
  `harness.db.password` or `harness.db-password`).

### Changed
- Response headers: a CSP that names every directive without `'unsafe-inline'`,
  Permissions-Policy, COOP/COEP/CORP; content-hashed assets cached for a year, pages and API
  `no-store`.
- Security version floors in the root pom (14 entries, four of them BOMs: Jackson, Netty, OpenTelemetry, Log4j), ahead of the Spring Boot
  BOM.
- The suites no longer spend failed sign-ins on shared seeded accounts.
- Project version 1.1.0 (was 1.0.0-SNAPSHOT).

### Fixed — found while closing the gaps
- 41 known vulnerabilities in transitive dependencies (OSV), in two rounds of floors.
- 6 header findings from the first ZAP baseline.
- `install -DskipTests` still ran the Gatling simulation (found on Windows).
- `th.cmd` → `mvnw.cmd`: `cmd.exe` split `-Dname=value` arguments at `=`.
- Visual captures could include a focus ring under the pointer; the pointer is parked and
  two frames are awaited.
- A JaCoCo agent reset in one test erased the coverage of the other tests in the same JVM.
- A failure without a message was summarised as an empty line.
- The PortSync SSE scenario used a path PortSync does not serve.
- Known accessibility issues were matched by exact selector, which broke as soon as axe
  needed structural selectors for several elements.
- Known accessibility issues never matched in CI: the scanner stored axe's target list as
  `[.tone-warn]`. Found once failure messages named the unlisted elements first.
- Re-run attempts on `main` stored the passed jobs' history twice.
- Gatling's `1,500` was parsed as 0 requests (capacity experiment and CI summary).
- The AI evaluation could end "success" without a result (missing `pipefail`).
- A navigation that hits `Timed out receiving message from renderer` (EdgeDriver on Windows) is
  repeated once; any other timeout still fails at once.
- Two accessibility defects found in PortSync (contrast 4.21:1 and 3.03:1) were fixed in
  PortSync 1.1.0, together with a third its new contrast test found; the adapter lists no
  known issues any more.

### Measured
- Local `clean install` (2 vCPU): **534 JVM tests, 0 failed**, 6 min 32 s.
- OSV: 41 advisories on the first scan → 0 untriaged. ZAP: 8 alert types → 6 fixed, now 0 warnings.
- Windows: 500 tests, 0 failed. TeamCity: every build of the chain green. CIForge in Actions:
  9 of 9 builds, 5 min 25 s.
- Capacity on 2 CPUs: 25 flows/s with sign-in on every flow, 200 without (three runners each).
- Repeats: fault seeding 46 of 48 cells stable over three runs; flaky demo quarantined 3 of 3;
  TIA 18 of 18 bugs caught by the selected subset, 3.8–33.2 % saved.
- AI drafter, step reuse on 8 stories × 2: qwen2.5 7B 68.5 % and 69.9 % (two runs), qwen2.5 1.5B 43.3 %, llama3.2 3B 36.3 %.

### Open
- The Firefox `cancel-dialog` visual check failed intermittently in CI on 2026-10-20 and was
  never reproduced locally (RESULTS §40).


## [1.0.0] — 2026-10-16 — Phase 5: the pipeline in three CI systems

### Added
- **GitHub Actions:** the UI suite sharded 3 browsers × 2 shards (`harness.shard`); blocking
  jobs exclude quarantined tests (`quarantine.json`, committed from the flaky-demo results); a
  non-blocking `quarantined` job runs only them; one `quality-gate` job — the check to require —
  passes only when every blocking job succeeded (skipped or cancelled count as failures);
  `teamcity-dsl` job.
- **TeamCity Kotlin DSL** (`.teamcity/settings.kts`, `.teamcity/pom.xml`): the same chain with
  snapshot and artifact dependencies, a composite quality gate, a non-blocking quarantine build
  and a "test count dropped by 20 %" failure condition. Compiled and generated in CI against a
  throwaway TeamCity server; **not run on a TeamCity agent**.
- **CIForge pipeline** (`.ciforge.yml`), executed: 9 builds, 8 min 11 s, green
  (`experiments/ciforge`).
- **INTERVIEW.md**: a two-minute pitch and 25 questions with honest answers.

### Fixed — found while building Phase 5
- **TeamCity DSL** (`e7ab21a`, `dfd0181`, `9151cdc`): wrong version of the core DSL artifact; a
  Kotlin compiler crash on `BuildType` subclasses with default constructor arguments; objects
  capturing the script instance.

### Measured
GitHub Actions run 38863031687: 18 jobs green in 14 min 40 s, UI shards 15 + 14 = 29 in each
browser. CIForge: 158 suite tests, 0 failed, 8 min 11 s.

### Could not be run
- A TeamCity agent (no server with agents available).

## [1.0.0-phase4] — 2026-10-16 — Quality dashboard, CLI, new-test guide, PortSync adapter

### Added
- **Quality dashboard** (`dashboard/`, Vite + React 19 + TypeScript): pass rate over time,
  failure causes, flakiness leaderboard, quarantine, slowest tests, coverage by module,
  mutation score and survivors, axe results, visual changes awaiting approval, defect
  detection matrix, test-impact results, healed locators and a list of missing data sources.
  Hand-written SVG charts on a validated palette, light and dark, table view for every
  chart, status always icon + word. Built into one `index.html` committed into
  `harness-insights`, so it needs no Node to use and opens from disk.
- **`th dashboard`** (`--open`, `--serve`, `--jdbc-url`, `--jsonl`) and `DashboardData`, which
  reads only the files runs leave behind and lists what is missing.
- **PortSync adapter** (`adapters/portsync`): API, contract (strict schemas for every core and
  simulator response), differential ISO 6346, event-handling, alert-lifecycle, UI, axe and
  BDD suites against github.com/RaktimChandra/PortSync, run in CI against PortSync's own
  Compose stack (`.github/workflows/portsync.yml`); offline contract checks in every build.
- **`docs/NEW_TEST_IN_5_MINUTES.md`** with a committed, passing worked example: valid J and Z
  container categories (API data rows + BDD examples) and a direction-echo API test.
- One run id per Maven build (`harness.run.id` from the root POM).
- CI: `dashboard` job (tests, typecheck, committed page = rebuilt page).
- Windows guide steps 10–13: `th`, flakiness, dashboard, PortSync.

### Fixed — found while building Phase 4
- `f3fd133` **Framework tests**: `harness-insights` coverage read 9 % because `TiaTest`'s JaCoCo
  resets erased other test classes' data; one JVM per test class → 77.5 %. Found by the
  dashboard.
- `ecbc3f5` **Build rules**: the no-sleep / no-parking / no-stdout ArchUnit rules covered only
  `harness/`; now every suite and the adapter.
- `b4acecf` **Coverage gap**: no test used a J or Z category container number.
- `c57ab74` **Docker**: the SUT image build did not copy `adapters/`.
- `26c798a` **Adapter**: the Cucumber runner was not included by surefire, so no PortSync
  scenario ran in the first CI run.

### Measured
485 JVM tests + 31 + 9 Vitest + 1 Gatling simulation, 0 failures, 5 min 54 s (RESULTS §23).
PortSync: 42 tests, 0 failures, 0 axe violations, in CI (§24).

### Could not be run in the authoring sandbox
- PortSync (needs Kafka; Docker Hub and Maven Central blocked) — CI only.

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
