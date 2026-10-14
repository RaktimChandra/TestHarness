# Framework guide

How to write each kind of test in TestHarness. Every snippet below is taken from, or
compiles against, code in this repository.

- [Project layout](#project-layout)
- [Configuration](#configuration)
- [API tests](#api-tests)
- [UI tests](#ui-tests)
- [BDD scenarios](#bdd-scenarios)
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
| `ui.binary.<browser>` / `ui.driver-path.<browser>` | empty | custom browser/driver executables |
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
| Every API response matches a schema | `SchemaValidationFilter` (strict) | contracts checked everywhere |
| No secret fallbacks in CI | `HarnessConfig.secret` | a missing CI secret fails loudly |
| Retry needs a cap and a predicate | `Retry.Builder.build()` | no unbounded or blanket retries |
