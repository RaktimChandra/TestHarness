# Framework guide

How to write each kind of test in TestHarness. Every snippet below is taken from, or
compiles against, code in this repository.

- [Project layout](#project-layout)
- [Configuration](#configuration)
- [API tests](#api-tests)
- [UI tests](#ui-tests)
- [BDD scenarios](#bdd-scenarios)
- [Visual regression](#visual-regression)
- [Accessibility](#accessibility)
- [Contract tests (Pact)](#contract-tests-pact)
- [Performance smoke (Gatling)](#performance-smoke-gatling)
- [Security smoke tests](#security-smoke-tests)
- [Selenium Grid](#selenium-grid)
- [The `th` command](#the-th-command)
- [History, flakiness and quarantine](#history-flakiness-and-quarantine)
- [Self-healing locators](#self-healing-locators)
- [Test impact analysis](#test-impact-analysis)
- [Mutation testing](#mutation-testing)
- [AI drafts (optional)](#ai-drafts-optional)
- [Test data](#test-data)
- [Reporting and evidence](#reporting-and-evidence)
- [Rules the build enforces](#rules-the-build-enforces)

## Project layout

| Path | What lives there | Depends on the SUT? |
|---|---|---|
| `harness/harness-core` | config, secrets, logging context, `Retry`, `Await`, ArchUnit rules | no |
| `harness/harness-reporting` | `Report` (Allure steps/attachments), run metadata, history | no |
| `harness/harness-data` | `TestData` (seeded Faker), `UniqueIds`, ISO 6346 oracle, `@JsonCases`, `@CsvRecords`, `PostgresFixture` | no |
| `harness/harness-api` | `ApiSpecs`, `SchemaRegistry` + validation filter, `TokenCache`, `ResponseAssert` | no |
| `harness/harness-ui` | `Drivers`, `DriverFactory`, `Waits`, `BasePage`, `Component`, `Locators`, `Inputs`, `BrowserExtension`, `UiEvidence` | no |
| `harness/harness-bdd` | `ScenarioContext`, `ScenarioHooks` | no |
| `harness/harness-insights` | history, flakiness, quarantine/selection/shard filters, failure classes, test impact analysis, AI drafts, dashboard data + prebuilt page | no |
| `harness/harness-cli` | the `th` command | no |
| `suites/suite-support` | `GateSut` launcher, `GateApi`, `GateFixtures`, models, schemas, page objects | **yes** |
| `suites/*-suite` | the tests (`flaky-demo` only runs with `-DskipFlakyDemo=false`) | yes |
| `adapters/portsync` | the PortSync adapter: config, clients, schemas, page objects, live suites (`-Dportsync.live`) | PortSync |
| `dashboard/` | source of the quality dashboard (Vite + React + TypeScript) | no |
| `tools/` | experiment scripts (`tia_experiment.py`) | — |
| `experiments/` | committed results of the experiments | — |

## Configuration

Keys are read from (later wins) `harness-defaults.yml` → `harness.yml` →
`harness-<env>.yml` → `HARNESS_*` environment variables → `-Dharness.*` properties.
Choose the environment with `-Dharness.env=docker` or `HARNESS_ENV=docker`.

| Key | Default | Meaning |
|---|---|---|
| `sut.mode` | `embedded` | `embedded` starts PostgreSQL + the app in the test JVM; `external` uses `sut.base-url` |
| `sut.base-url` | — | required in `external` mode (`docker`: `http://localhost:8080`) |
| `sut.seeded-bugs` | empty | comma-separated seeded bugs to start the embedded SUT with |
| `api.schema-validation` | `strict` | `strict` / `lenient` / `off` |
| `ui.browser` | `chrome` | `chrome` / `firefox` / `edge` |
| `ui.headless` | `true` | `false` to watch |
| `ui.explicit-wait` / `ui.poll-interval` | `10s` / `100ms` | wait budget |
| `ui.grid-url` | empty | a Selenium Grid instead of local browsers |
| `ui.session-attempts` | `2` | tries to start a browser; retried only on `SessionNotCreatedException` |
| `ui.binary.<browser>` / `ui.driver-path.<browser>` | empty | custom browser/driver executables |
| `sut.browser-base-url` | `sut.base-url` | where *browsers* open the app (Grid: `http://gate:8080`) |
| `visual.platform` | `<browser>-<os>` | baseline set to compare with (CI: `chrome-ci`, `firefox-ci`, `edge-ci` on Linux; `chrome-windows-ci`, `edge-windows-ci` on Windows) |
| `visual.mode` | `compare` | `update` writes the current rendering as the baseline |
| `visual.max-mismatch` / `visual.pixel-tolerance` | `0.002` / `0.1` | allowed share of differing pixels / per-pixel YIQ tolerance |
| `visual.require-baseline` | `true` in `ci`, else `false` | a missing baseline fails instead of skipping |
| `a11y.max-critical` / `-serious` / `-moderate` / `-minor` | `0` / `0` / `-1` / `-1` | violations allowed per impact (`-1` = report only) |
| `a11y.known-issues` | empty | `rule@selector, ...`: violations in a system you cannot change, reported but not counted; the selector is read as a descendant chain (`color-contrast@.hull-label .code.muted`); any other element still fails |
| `ui.healing` | `report` | `report` (heal and mark), `fail` (diagnose and fail), `off` |
| `history.enabled` / `history.dir` | `true` / `target/test-history` | JSON Lines history of every result |
| `history.jdbc-url` / `history.db-user` | empty / `harness` | also store history in PostgreSQL (password: `HARNESS_HISTORY_DB_PASSWORD`) |
| `quarantine.mode` / `quarantine.file` | `off` / `quarantine.json` | `exclude` (blocking jobs) or `only` (quarantine job) |
| `select` | empty | file of test keys to run (`-Dharness.select=...`) |
| `shard` | empty | `k/n`: run only this shard (`-Dharness.shard=2/4`) |
| `shard.durations` | empty | JSON of mean test durations (`th history durations`): shards are balanced longest-first; tests not in the file use the hash |
| `tia.record` / `tia.dir` | `false` / `target/tia` | record per-test coverage (needs sequential tests) |
| `ai.provider` / `ai.model` / `ai.base-url` | `none` / — / provider default | optional model for drafts (`HARNESS_AI_API_KEY`) |
| `data.seed` | random | fix the Faker seed to replay a run |

Any key can also come from the environment: `HARNESS_DB_PASSWORD` reaches `harness.db.password`
or `harness.db-password` (an `_` matches `.` or `-`).

Secrets are **only** environment variables, read with `config.secret("NAME", localFallback)`.
In `ci` there is no fallback. Current secrets: `GATE_SEED_PASSWORD`,
`GATE_TEST_SUPPORT_KEY` (external mode), `HARNESS_DB_PASSWORD` (external DB).

```java
HarnessConfig config = HarnessConfig.get();
Duration wait = config.duration("ui.explicit-wait", Duration.ofSeconds(10));
Secret key = config.secret("GATE_TEST_SUPPORT_KEY", "local-test-support-key");
log.info("using {}", key); // prints GATE_TEST_SUPPORT_KEY=****
```

## API tests

Extend `ApiTest` (logging context, `api`, `fx`). Use `raw*` methods when the response *is*
the thing under test; use the typed helpers to set up preconditions.

```java
@Test
void capacity_is_enforced_at_the_boundary() {
    Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(12, 0), 2);      // own lane, own data
    api.book(fx.newDriver(), slot.id(), GateFixtures.container());       // typed, asserts 201
    api.book(fx.newDriver(), slot.id(), GateFixtures.container());
    Response third = api.rawBook(fx.newDriver(), slot.id(), GateFixtures.container(), "DROP_OFF");
    assertThatResponse(third).hasStatus(409).hasProblemCode("SLOT_FULL");
}
```

**Schema validation is automatic.** If you add an endpoint, add a rule to
`suites/suite-support/src/main/resources/schemas/registry.yml` and a schema next to it —
in `strict` mode an unregistered response fails the test with
`Schema validation failed: no schema registered for ...`.

```yaml
rules:
  - {method: POST, path: "/api/bookings/{reference}/cancel", status: "200", schema: booking.json}
  - {method: "*",  path: "**",                               status: 4xx,   schema: problem.json}
```

Exact statuses beat status classes; concrete paths beat `**`.

**Data-driven API tests** read cases from JSON (or CSV with `@CsvRecords`):

```java
record ContainerCase(String name, String value, int status, String code) {}

@ParameterizedTest(name = "{0}")
@JsonCases(value = "/data/container-cases.json", type = ContainerCase.class)
void container_number_rules_are_enforced_by_the_api(ContainerCase c) { ... }
```

**Tests that change global state** (clock, seeded bugs) go in a class annotated
`@Isolated` and restore the state in `@AfterEach`.

## UI tests

Extend `UiTest` (fresh browser per test on its own thread; evidence on failure). Talk to
pages, never to locators.

```java
@Test
void a_driver_books_a_slot_and_sees_it_in_their_bookings() {
    GateUser driver = fx.newDriver();
    Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(9, 0), 2);           // set up via API
    DriverPage page = pages().driverSignedInViaApi(driver);                 // skip the login form
    page.slots().showDate(slot.start().toLocalDate()).select(slot.id());
    String reference = page.enterContainer(GateFixtures.container()).submitAndGetReference();
    page.waitForStatus(reference, "BOOKED");
    assertThat(page.slotText(reference)).contains("09:00–09:30");
}
```

Writing a page object:

```java
public class ClerkPage extends BasePage<ClerkPage> {
    static final By FORM = testId("search-form");          // data-testid is the UI contract

    @Override protected By readyLocator() { return FORM; } // "this page has rendered"
    @Override protected String path() { return "/clerk"; }

    public ClerkPage checkIn(String reference) {
        Report.step("Check in " + reference, () -> {
            click(testId("checkin-" + reference));
            waits.attribute(testId("result-status-" + reference), "data-status", "CHECKED_IN");
        });
        return this;
    }
}
```

Waiting: use `Waits` (`visible`, `clickable`, `text`, `textContains`, `attribute`,
`count`, `gone`, `urlContains`, or `until(description, condition)`). Every wait says what
it waited for; a timeout reads like `waiting 10000 ms for By.cssSelector:
[data-testid="book-submit"] to be clickable`.

Run in another browser: `-Dharness.ui.browser=firefox` (or `edge`). Watch it:
`-Dharness.ui.headless=false`.

## BDD scenarios

Feature files live in `suites/bdd-suite/src/test/resources/features`. Steps share state
through the injected `World` (one per scenario):

```gherkin
@api @regression
Scenario: A slot cannot take more trucks than its capacity
  Given a gate slot tomorrow at 10:00 with room for 2 trucks
  And 2 other drivers have already booked that slot
  And I am a driver
  When I book that slot for a valid container
  Then the booking is refused because "SLOT_FULL"
```

```java
public class BookingSteps {
    private final World w;
    public BookingSteps(World world) { this.w = world; }        // PicoContainer injects it

    @Given("a gate slot tomorrow at {time} with room for {int} truck(s)")
    public void slotTomorrow(LocalTime time, int capacity) {
        w.ctx.put(World.SLOT, w.fx.slotTomorrowAt(time, capacity));
    }
}
```

Tag a scenario `@ui` and the framework hooks start a browser before it and quit it
after (capturing evidence on failure). Filter with
`-Dcucumber.filter.tags="@smoke and not @ui"`.

## Visual regression

```java
@Test
void login_error(WebDriver driver, Waits waits) {
    var login = pages().login().open();
    login.enter("driver1", "wrong-password").submit();
    login.error();
    new VisualCheck(driver, VisualSettings.from(HarnessConfig.get(), "chrome"))
            .matches("login-form-error", waits.visible(testId("login-form")),
                     testId("login-username"));          // ignore-region: masked child
}
```

- Screenshot **one component**, not the page; mask anything that changes between runs.
- Keep the layout around it deterministic (e.g. give the test its own date if a list
  above it shows other tests' data) — the diff has no anti-aliasing detection.
- The page is stabilised for you before capture (focus blurred, carets/animations off).
- New or changed baseline: run the **visual-baselines** workflow (Actions → visual-baselines
  → Run workflow, on your branch), review the PNGs on the `visual-baselines/<run>` branch it
  pushes, and merge it. Locally: `-Dharness.visual.mode=update` writes your machine's
  baselines under its own platform name (not committed).
- A failure leaves `target/visual/diff/<platform>/<name>-actual.png` and `-diff.png`
  (red = changed) and attaches baseline, actual and diff to the Allure report.

## Accessibility

```java
private final A11yGate gate = A11yGate.from(HarnessConfig.get());

@Test
void driver_page_with_bookings_validation_and_dialog(WebDriver driver, Waits waits) {
    DriverPage page = pages().driverSignedInViaApi(fx.newDriver());
    var scanner = new A11yScanner(driver, "chrome", Path.of("target", "a11y"));
    scanner.assertWithin("driver", gate);               // scan + gate
    page.enterContainer("CSQU3054384");
    scanner.assertWithin("driver-validation-error", gate); // scan the error *state* too
}
```

Each scan writes `target/a11y/<page>-<browser>.json`; `.github/scripts/a11y_summary.py`
turns them into a per-page × impact table. Only WCAG 2.0/2.1 A and AA rules run.

## Contract tests (Pact)

- **Consumer** (`sut/gate-ui/src/api/client.pact.test.js`): call the real client against
  the Pact mock server; one interaction per request/response the UI depends on. Use
  matchers (`like`, `eachLike`, `regex`, `datetime`, `integer`) for values that vary,
  exact values only where the UI branches on them.
  `./mvnw verify -pl sut/gate-ui` (or `npm test` in `sut/gate-ui` if you have Node)
  regenerates `contracts/pacts/gate-ui-gate-api.json` — commit it; CI fails if the
  committed file differs from the regenerated one.
- **Provider** (`sut/gate-api/.../PactProviderTest`): every `given(...)` state in the
  contract needs a `@State` method that creates exactly that data.
  `./mvnw verify -pl sut/gate-api -am` runs it.

## Performance smoke (Gatling)

```bash
./mvnw verify -pl suites/perf-suite -am                       # default budget
./mvnw verify -pl suites/perf-suite -am -Dperf.users=200 -Dperf.ramp=60 -Dperf.p95=500
./mvnw verify -DskipPerf                                       # build everything else
```

The HTML report is under `suites/perf-suite/target/gatling/<run>/index.html`. Gates
(p95, p99, error %) fail the build; change them with `-Dperf.*`, not by editing the
simulation.

## Security smoke tests

`SecuritySmokeTest` in the API suite (`-Dgroups=security` to run only these). Add a test
when you add an endpoint that takes an identifier (IDOR), a role (authorisation) or free
text (injection/reflection). Expected behaviour is always the normal error contract —
a 401/403/404/400 *problem* — never a 500 or a stack trace.

## Selenium Grid

```bash
docker compose -f docker-compose.yml -f docker-compose.grid.yml up -d --build --wait
HARNESS_ENV=docker ./mvnw verify -pl suites/ui-suite -am \
  -Dharness.ui.grid-url=http://localhost:4444 \
  -Dharness.sut.browser-base-url=http://gate:8080 \
  -Dharness.ui.browser=firefox -DexcludedGroups=visual
```

Visual checks are excluded on Grid because the baselines are rendered on the CI runner's
browsers, not the node images. Watch a session live at http://localhost:4444 (Sessions →
camera icon; the nodes run noVNC without a password).

## The `th` command

`./th` (Windows: `th.cmd`) builds the CLI on first use and wraps the common tasks:

```bash
./th run --suite ui --browser firefox --tags smoke      # -> mvnw verify -pl suites/ui-suite ...
./th run --suite api,bdd --env docker --workers 8
./th run --suite ui --shard 2/4 --quarantine exclude
./th run --suite api --dry-run                           # print the Maven command only
./th rerun-failed                                        # only what failed last time
./th report                                              # generate Allure and open it
./th history flaky --jsonl suites/api-suite/target/test-history
./th tia select --base origin/main
./th ai explain                                          # summary of the last failures
./th dashboard --open                                    # quality dashboard from the last runs
./th <command> --help
```

A **test key** names a test everywhere (history, quarantine, selection, impact analysis):
`io.example.SlotApiTest#capacity_is_enforced` for JUnit, `features/booking_rules.feature:17`
for a Cucumber scenario or example row.

## History, flakiness and quarantine

Every suite records its results automatically to `target/test-history/<suite>-<run>.jsonl`.
To keep history across runs, give it a PostgreSQL database:

```bash
docker run -d --name history -e POSTGRES_USER=harness -e POSTGRES_PASSWORD=harness -e POSTGRES_DB=history \
  -p 5433:5432 postgres:16-alpine
./mvnw verify -Dharness.history.jdbc-url=jdbc:postgresql://localhost:5433/history
./th history ingest --jdbc-url jdbc:postgresql://localhost:5433/history suites/*/target/test-history
./th history flaky --jdbc-url jdbc:postgresql://localhost:5433/history
./th history quarantine --jdbc-url jdbc:postgresql://localhost:5433/history --write quarantine.json
```

Flakiness only counts pass↔fail flips between runs **of the same commit**. With
`quarantine.json` committed, blocking CI jobs run with `-Dharness.quarantine.mode=exclude` and a
non-blocking job runs `-Dharness.quarantine.mode=only`. Locally the default is `off`.

Each failure is classified (locator, timeout, assertion, environment, data, unknown) in the
history, the leaderboard and the failure summary.

**In CI** the history needs no database: after every pipeline on `main` the `publish` job
appends the run's JSON Lines files to the `ci-history` branch (newest 120 runs), writes
`shard-durations.json`, `flaky.txt` and `quarantine-proposal.txt` there, and publishes the
dashboard built from that window to `gh-pages`. The same files work locally:

```bash
git fetch origin ci-history && git worktree add ../ci-history origin/ci-history
./th history flaky --jsonl ../ci-history/runs
./th history durations --jsonl ../ci-history/runs --suite ui-suite --shards 2   # the planned split
./mvnw verify -pl suites/ui-suite -Dharness.shard=1/2 -Dharness.shard.durations=../ci-history/shard-durations.json
```

The deliberately flaky demo lives in `suites/flaky-demo` and runs only with
`-DskipFlakyDemo=false`.

## Self-healing locators

Describe important controls as a `Target` with fallbacks; page objects then `click`, `type` and
`find` targets like locators:

```java
static final Target SUBMIT = Target.named("Book slot button")
        .testId("book-submit")                    // the contract (rank 0)
        .accessibleName("button", "Book slot")    // fallback 1: what assistive tech sees
        .text("button", "Book slot");             // fallback 2

click(SUBMIT);
```

If `book-submit` is renamed, the click still works through the accessible name, and the test
is marked **healed**: a yellow step and a `healed` tag in Allure, a warning in the log, and a
line in `target/healing/healing.jsonl` with the fix ("the element's data-testid is now
`book-button`: update the Target…"). A primary locator that matches two elements fails
immediately; ambiguous fallbacks are never used. Run CI with `-Dharness.ui.healing=fail` if a
heal should block a merge.

## Test impact analysis

```bash
# 1. Record which SUT files each test executes (sequential; ~3.5 min on a 2-vCPU machine)
./mvnw verify -pl suites/api-suite,suites/ui-suite,suites/bdd-suite \
  -Dharness.tia.record=true -Djunit.jupiter.execution.parallel.enabled=false
# 2. Build the map
./th tia map --commit "$(git rev-parse HEAD)"
# 3. Select the tests for your change and run them
./th tia select --base origin/main
./th run --suite api,ui,bdd --select target/tia/selection.txt
```

The selector explains every file it was given; changes to build, framework or test code select
everything, frontend changes select all UI and BDD tests, and a changed SUT file that no test
executes is reported as **untested**. The experiment behind it is `tools/tia_experiment.py`.

## Mutation testing

```bash
./mvnw -Pmutation verify -pl sut/gate-domain     # report: sut/gate-domain/target/pit-reports/index.html
```

The build fails below 95 % (`pit.threshold`). A surviving mutant is a behaviour no test pins
down: write the test that kills it (see `SurvivingMutantsTest`).

## AI drafts (optional)

Nothing needs a model. To use one:

```bash
export HARNESS_AI_API_KEY=...                 # never in a file
./th ai gherkin -Dharness.ai.provider=openai -Dharness.ai.model=<model> \
  --story "As a driver I want to be told when a slot I wanted frees up"
```

(`-D` options can also go in `harness.yml` under `ai:`; for Ollama use `ai.provider=openai`,
`ai.base-url=http://localhost:11434/v1`, no key.) The draft is written to
`target/ai-drafts/<story>.feature` with a banner and `@draft`; it is parsed, but never run or
committed. `./th ai explain` writes `target/ai-drafts/failure-summary.md`; with a model it adds a
section marked as an unverified draft.

## Test data

| Need | Use |
|---|---|
| A user nobody else uses | `fx.newDriver()`, `fx.newClerk()` |
| A seeded demo user | `GateFixtures.seeded("clerk1")` |
| A slot nobody else uses | `fx.slotTomorrowAt(time, capacity)`, `fx.slotStartingIn(duration, capacity)` |
| A valid container number | `GateFixtures.container()` |
| The remainder-10 edge case | `GateFixtures.remainderTenContainer()` |
| A wrong check digit | `ContainerNumbers.withWrongCheckDigit(valid)` |
| Reproducible random data | `-Dharness.data.seed=<seed from the log>` |
| Grey-box DB check (embedded mode) | `GateSut.get().database().orElseThrow().jdbc().count("...", params)` |

## Reporting and evidence

```java
Report.step("Clerk opens slot " + lane, () -> api.createSlot(...));   // a timeline step
Report.parameter("browser", "firefox");
Report.json("request body", body);
```

Request/response pairs are attached automatically to API steps. UI failures get a
screenshot, page HTML, URL and the browser console (via WebDriver BiDi) in the report and
under `target/evidence/<test>/`.

Generate and view the report:

```
mvnw -N -Preport verify
jwebserver -d <absolute path>/target/allure-report -p 8000
```

## Quality dashboard

```bash
./th dashboard                    # writes target/dashboard/index.html (opens from disk)
./th dashboard --open             # …and opens it
./th dashboard --serve            # serves it on http://localhost:8001
./th dashboard --jdbc-url jdbc:postgresql://localhost:5433/history   # include PostgreSQL history
```

It reads only what runs left on disk (see DESIGN D42) and lists every missing source under
"Data sources". To change the page: edit `dashboard/src`, then `cd dashboard && npm ci && npm
test && npm run build` — the build writes the single-file page into
`harness/harness-insights/src/main/resources/.../dashboard/site/index.html`; commit both (CI
fails if they differ).

## Another system under test: the PortSync adapter

`adapters/portsync` is the template for pointing the framework at a different application;
see its [README](adapters/portsync/README.md). In short: a config record for URLs, API clients
built with `ApiSpecs.base(url, SchemaRegistry.load("<your>-schemas"), config)`, a schema
registry in strict mode, page objects extending `BasePage` with `Target`s, and live tests kept
behind a property so the default build does not need the other system running.

Adding a single test to the existing suites: [docs/NEW_TEST_IN_5_MINUTES.md](docs/NEW_TEST_IN_5_MINUTES.md).

## Rules the build enforces

| Rule | Where | Why |
|---|---|---|
| No `Thread.sleep` / `TimeUnit.sleep` (framework, suites and adapter) | `HarnessArchRules.NO_THREAD_SLEEP` | wait on conditions, not time |
| `LockSupport.park*` only in `core.retry.Pause` | `PARKING_ONLY_IN_PAUSE` | one audited place that blocks |
| No `System.out` / `System.err` | `NO_STANDARD_STREAMS` | logs are structured |
| Implicit waits = 0 | `DriverFactory` | explicit waits only |
| Every API response matches a schema | `SchemaValidationFilter` (strict); non-JSON must be registered as `none` | contracts checked everywhere |
| No critical/serious WCAG violations | `A11yGate` | accessibility regressions fail the build |
| Committed Pact file = regenerated file | CI `git diff --exit-code -- contracts/` | the provider verifies what the UI really sends |
| p95 / p99 / error-rate budgets | `BookingFlowSimulation` assertions | latency regressions fail the build |
| No secret fallbacks in CI | `HarnessConfig.secret` | a missing CI secret fails loudly |
| Retry needs a cap and a predicate | `Retry.Builder.build()` | no unbounded or blanket retries |
| A locator matches exactly one element | `Healer` | ambiguity is a test bug; heals are always reported |
| Domain mutation score ≥ 95 % | PIT `mutation` profile | untested behaviour in the rules fails the build |
| Committed dashboard page = rebuilt page | CI `dashboard` job | the page shipped in the jar is the reviewed source |
