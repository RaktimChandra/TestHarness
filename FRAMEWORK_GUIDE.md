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
| `suites/suite-support` | `GateSut` launcher, `GateApi`, `GateFixtures`, models, schemas, page objects | **yes** |
| `suites/*-suite` | the tests | yes |

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
| `visual.platform` | `<browser>-<os>` | baseline set to compare with (CI: `chrome-ci`, `firefox-ci`, `edge-ci`) |
| `visual.mode` | `compare` | `update` writes the current rendering as the baseline |
| `visual.max-mismatch` / `visual.pixel-tolerance` | `0.002` / `0.1` | allowed share of differing pixels / per-pixel YIQ tolerance |
| `visual.require-baseline` | `true` in `ci`, else `false` | a missing baseline fails instead of skipping |
| `a11y.max-critical` / `-serious` / `-moderate` / `-minor` | `0` / `0` / `-1` / `-1` | violations allowed per impact (`-1` = report only) |
| `data.seed` | random | fix the Faker seed to replay a run |

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

## Rules the build enforces

| Rule | Where | Why |
|---|---|---|
| No `Thread.sleep` / `TimeUnit.sleep` | `HarnessArchRules.NO_THREAD_SLEEP` | wait on conditions, not time |
| `LockSupport.park*` only in `core.retry.Pause` | `PARKING_ONLY_IN_PAUSE` | one audited place that blocks |
| No `System.out` / `System.err` | `NO_STANDARD_STREAMS` | logs are structured |
| Implicit waits = 0 | `DriverFactory` | explicit waits only |
| Every API response matches a schema | `SchemaValidationFilter` (strict); non-JSON must be registered as `none` | contracts checked everywhere |
| No critical/serious WCAG violations | `A11yGate` | accessibility regressions fail the build |
| Committed Pact file = regenerated file | CI `git diff --exit-code -- contracts/` | the provider verifies what the UI really sends |
| p95 / p99 / error-rate budgets | `BookingFlowSimulation` assertions | latency regressions fail the build |
| No secret fallbacks in CI | `HarnessConfig.secret` | a missing CI secret fails loudly |
| Retry needs a cap and a predicate | `Retry.Builder.build()` | no unbounded or blanket retries |
