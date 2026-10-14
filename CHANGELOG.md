# Changelog

All notable changes, one section per phase. Each phase ends with the whole project
compiling, every test passing, and `./mvnw verify` running end to end.

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
