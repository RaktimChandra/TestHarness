# Add a new test in 5 minutes

Three real changes, from smallest to largest, each already committed and passing. Do them in
order the first time; afterwards, jump to the one that fits.

| You want to… | Do this | Code to write | Example below |
|---|---|---|---|
| Try one more input against a rule that is already tested | add a row to a data file or an `Examples:` table | none | steps 1 and 2 |
| Check a new behaviour of the API | add a test method to the suite's class | ~8 lines | step 3 |
| Check a new behaviour in a browser | add a page-object method and a test | ~15 lines | "UI tests" at the end |

Before you start (once): JDK 21 and Docker running, then build everything once so single
suites can run on their own:

```bash
./mvnw install -DskipTests        # PowerShell: .\mvnw.cmd install -DskipTests
```

All commands below run in **one terminal at the repository root** (PowerShell on Windows: use
`.\mvnw.cmd` instead of `./mvnw` and put `-D...` arguments in double quotes).

---

## The worked example: categories J and Z

ISO 6346 has three equipment categories: **U** (freight container), **J** (detachable
equipment) and **Z** (trailer or chassis). The Gate API accepts all three, but until this
example every valid number in the tests was a `U`. A bug that rejected `J` or `Z` would have
gone unnoticed.

### Step 1 — one more data row (1 minute, no code)

The API rule "container numbers must be valid" is a data-driven test:
`BookingApiTest.container_number_rules_are_enforced_by_the_api` runs once per entry in
`suites/api-suite/src/test/resources/data/container-cases.json`. Add two entries:

```json
  {"name": "category J (detachable equipment) is accepted", "value": "TGHJ0001239", "status": 201, "code": null},
  {"name": "category Z (trailer or chassis) is accepted",  "value": "APZZ9999990", "status": 201, "code": null},
```

The check digits were computed with the test-side oracle
(`ContainerNumbers.checkDigit("TGHJ000123")` → 9), never by the system under test.

### Step 2 — the same rule in business language (1 minute, no code)

`suites/bdd-suite/src/test/resources/features/booking_rules.feature` has a Scenario Outline for
the same rule. Add two rows to its `Examples:` table — the steps already exist:

```gherkin
      | HLXJ4820152 | confirmed                          |
      | OOLZ7302641 | confirmed                          |
```

Different numbers from step 1 on purpose: one container may have only one open booking, and
readers of the feature file should not have to know which numbers the API suite used.

### Step 3 — a new API test (3 minutes)

The suite checked that a booking defaults to the right direction, but never that the direction
you ask for is the one you get. In `suites/api-suite/.../BookingApiTest.java`:

```java
@ParameterizedTest(name = "{0}")
@ValueSource(strings = {"PICK_UP", "DROP_OFF"})
void the_direction_asked_for_is_the_direction_booked(String direction) {
    Models.Slot slot = fx.slotTomorrowAt(LocalTime.of(15, 0), 1);
    Response r = api.rawBook(fx.newDriver(), slot.id(), GateFixtures.container(), direction);
    assertThatResponse(r).hasStatus(201).hasJsonValue("direction", direction);
}
```

What you got for free, without writing it:

- **Your own data.** `fx.newDriver()` creates a fresh user and `fx.slotTomorrowAt(...)` a slot
  in a lane nobody else uses, so the test can run in parallel with everything else.
- **A generated, valid container number** from the independent ISO 6346 oracle.
- **Contract checking.** Every response is validated against its JSON Schema in strict mode
  (`suites/suite-support/src/main/resources/schemas/registry.yml`). If you call an endpoint
  that has no rule there, the test fails and tells you to add one.
- **Evidence.** Request and response bodies in Allure, and in the console when the test fails.
- **History.** The result is recorded for flakiness scoring, quarantine and the dashboard.

### Run it

```bash
./mvnw verify -pl suites/api-suite -Dtest=BookingApiTest
./mvnw verify -pl suites/bdd-suite
```

Measured on the 2-vCPU authoring sandbox (2026-10-16): the API command took **54 s** (26 tests,
including starting PostgreSQL and the app), the BDD command **39 s** (17 scenarios). Or
through the CLI: `./th run --suite api --tags api` and `./th rerun-failed` after a failure.

### See it

```bash
./th report        # Allure: the new cases under "Booking API" and the feature
./th dashboard     # the run joins the pass-rate history; open target/dashboard/index.html
```

---

## Checklist before you push

- [ ] **No sleeps.** Wait for a condition (`Await.atMost(...)`, `Waits.visible(...)`).
      `Thread.sleep` fails the build (ArchUnit).
- [ ] **No shared state.** Create what you need with `fx.*`; never rely on another test's data.
- [ ] **Fixed values need cleanup.** A fixed container number must be released (cancel the
      booking), or a re-run against a persistent database will collide. Generated values don't.
- [ ] **One behaviour per test**, named as a sentence (`a_slot_cannot_start_in_the_past`).
- [ ] **New endpoint?** Add its rule and schema to `registry.yml`.
- [ ] **Asserted against an oracle, not the SUT's own code.** E.g. check digits come from
      `ContainerNumbers`, which shares no code with the app.
- [ ] Tag it: `@Tag("smoke")` if it guards a core journey; suites already carry `api`/`ui`.

## UI tests

Same pattern, one level up. Add an intention-revealing method to the page object in
`suites/suite-support/src/main/java/.../gate/ui/` (tests never hold locators), then a test in
`suites/ui-suite` that extends `UiTest` and takes `WebDriver` as a parameter. Prefer a `Target`
for anything a user clicks or types into:

```java
static final Target SUBMIT = Target.named("Book slot button")
        .testId("book-submit")                        // primary
        .accessibleName("button", "Book slot");      // fallback, used only if the primary breaks
```

If the primary locator breaks, the step still passes but is labelled **healed** in Allure with
the exact fix, and `-Dharness.ui.healing=fail` (use it in CI) turns that into a failure.
Run with `./mvnw verify -pl suites/ui-suite -Dtest=YourTest`; add
`-Dharness.ui.headless=false` to watch the browser.

## Another system under test

`adapters/portsync` points the same layers at a different application. Its
[README](../adapters/portsync/README.md) is the template for an adapter of your own: a config
record, API clients built on `ApiSpecs`, a schema registry, page objects with `Target`s.
