# TestHarness — interview notes

A two-minute pitch, then the questions an interviewer for a QA, SDET or cloud/DevOps role is
likely to ask, with honest answers. Every number links back to a run in
[RESULTS.md](RESULTS.md); if a number is not there, I don't quote it.

## The two-minute pitch

> Most test frameworks claim to catch bugs. I wanted one that **measures** it.
>
> TestHarness is a Java 21 framework — Selenium, REST Assured, Cucumber, JUnit 5,
> Testcontainers — plus a small but realistic system to test: a container-terminal gate
> booking app with a React UI, a Spring Boot API and PostgreSQL. That app has **six real bugs
> built in behind switches**: an off-by-one on capacity, an ISO 6346 check-digit edge case, a
> timezone shift, a race on the last place, a missing authorisation check, and validation that
> only exists in the UI.
>
> In CI I switch each bug on and run every suite type against it. The result is a defect
> detection matrix: the API suite caught all six; BDD five; contract, security and UI two
> each; accessibility, visual and performance none — which is correct for functional bugs.
> The race was caught by **one test out of 151**. And one of my own strategy assumptions was
> wrong: I expected Pact to catch the timezone bug, and it can't, because it matches
> timestamps by format. I kept that in the docs.
>
> On top of that: flakiness is measured from run history — a test that flips pass/fail on
> the same commit is scored and quarantined into a non-blocking job, never retried until
> green. Self-healing locators exist, but every heal is reported with the exact fix. Mutation
> testing took the domain rules from 95.1 % to 100 %. Test impact analysis caught all six bugs
> with a subset — but only saved 4–22 % of the time, and I explain why.
>
> Everything is one command: `./mvnw verify`, 485 tests in about six minutes on a 2-vCPU
> machine. CI runs it across Chrome, Firefox and Edge, sharded, on Selenium Grid, with a
> single quality gate. And to show it isn't tied to my demo app, the same framework tests
> PortSync — a Kafka event-processing system I also built — with 42 tests in CI.

## Questions and answers

### Framework design

**1. Why Java and this stack, rather than Playwright or Cypress?**
The roles I'm targeting mostly run Java and Selenium-based stacks, and the brief named it.
Selenium 4 also gave me things I wanted to learn properly: Grid, WebDriver BiDi for console
logs, relative locators. Playwright would make some UI code shorter; it wouldn't change the
measurement work, which is the point of the project.

**2. How is the framework separated from the app under test?**
`harness/` modules know nothing about the gate app; `suites/suite-support` holds everything
app-specific (launcher, API client, page objects, schemas). The proof is the PortSync adapter:
a different system driven by unchanged `harness/` code.

**3. How do tests run in parallel without interfering?**
Isolation by data, not teardown: each test creates its own users and its own lane of slots.
One browser per thread. The only shared resource is the database, and no test reads data it
didn't create. Fixed values (rare) are cleaned up and flagged in the guide.

**4. How do you avoid flaky waits?**
No sleeps at all — ArchUnit fails the build on `Thread.sleep`, and since Phase 4 that rule
also covers the suites, which I found it didn't before. Waits are explicit and describe what
they waited for, so a timeout message says what never happened.

**5. What does "contract on every response" mean?**
A REST Assured filter validates every response against a JSON Schema in strict mode. An
endpoint with no registered schema fails the test that called it. Every error must be an
RFC 9457 problem with a stable code, so tests assert on codes, not message text.

### Measuring the tests

**6. How does the fault-seeding experiment avoid false credit?**
A suite only gets credit for catching a bug if it was green on the same commit with no bug
switched on. Each (bug, suite) pair is its own CI job, so one failure can't contaminate another.

**7. What surprised you in the results?**
That the race condition was caught by exactly one test, and that Pact couldn't catch the
timezone shift. Also, part of the check-digit detection is luck: random container numbers hit
the remainder-10 case about one time in eleven. The dedicated tests catch it deterministically.

**8. What's your mutation score and is 100 % meaningful?**
143 mutants on the domain rules, STRONGER mutators: 95.1 % first, 100 % after turning the
seven survivors into tests. Each survivor was a real gap — e.g. tests checked that a validation
message existed, not what it said. 100 % on a small rules module is meaningful; I only run PIT
there, where mutants are cheap and the logic matters most.

**9. Does test impact analysis work?**
It caught all six seeded bugs with the selected subset, but saved only 3.8–21.6 % of
wall-clock. Most tests go through the same booking service, and start-up (JVM, Spring,
PostgreSQL) is a fixed cost. It pays off for narrow changes. I'd use it to order tests and
fail fast, not to skip them on main.

### Flakiness, healing, AI

**10. How do you define flaky?**
Flips between pass and fail on consecutive runs of the **same commit**, divided by the number
of transitions. Pass on one commit and fail on the next is a regression, not flakiness. The
demo test scored 0.368 over 20 CI runs and was quarantined; its stable twin scored 0.

**11. What does quarantine actually do?**
A JUnit Platform discovery filter reads `quarantine.json`. Blocking jobs exclude those tests;
a separate non-blocking job runs only them, so they keep producing history and can earn their
way out. No `@Disabled` edits, no retry-until-green — a retry would have hidden the real race.

**12. Isn't self-healing just hiding broken tests?**
It would be if it were silent. Here a heal is labelled "healed" in Allure with the exact fix
("data-testid is now book-button"), ambiguous matches are never used, and
`ui.healing=fail` turns any heal into a failure in CI. In the full UI suite there is one heal:
the demo that breaks a locator on purpose.

**13. What does the AI feature do, and did you test it with a real model?**
It drafts Gherkin from a user story and summarises failed runs, through any OpenAI-compatible
or Anthropic endpoint. Drafts are parsed, banner-marked, tagged `@draft` and never written into
the source tree. I tested the wire formats against stub servers; I have **not** run it against
a real provider, so I make no claims about output quality.

### CI/CD and cloud

**14. Walk me through the pipeline.**
Build once with framework and SUT tests; then API, BDD and UI suites in parallel — UI across
Chrome, Firefox and Edge, two shards each — plus Compose smoke, Selenium Grid, Gatling and PIT.
Quarantined tests run in a non-blocking job. One quality-gate job is the required check: green
only if every blocking job succeeded, and "skipped" counts as not passed.

**15. How does sharding work?**
A stable hash (CRC32) of each test's key modulo the shard count. It's deterministic, so a test
always lands in the same shard and history stays comparable. The trade-off is uneven shard
durations; a duration-aware split from history would be the next step.

**16. You wrote a TeamCity config — did you run it?**
The Kotlin DSL compiles and generates in CI, so the syntax and model are checked. It has
**not** run on a TeamCity server; agent requirements are declared, not proven. I say so in the
file header.

**17. What is CIForge?**
A small TeamCity-style CI orchestrator I wrote in Python (FastAPI, SQLite): build chains with
snapshot dependencies, artifact dependencies, VCS webhooks with HMAC, schedules, local and
Docker runners. TestHarness's own pipeline file runs on it; the run is recorded in RESULTS.md.
It is a learning project, not a TeamCity replacement.

**18. What did Selenium Grid teach you?**
That more workers isn't faster on one machine: 1 → 4 workers cut the test span 13–23 %, but 8
was slower than 4 on a 4-vCPU runner because browsers are CPU-bound. And networking: browsers in
containers can't reach `localhost`, and nodes must advertise a URL the client can reach or
WebDriver BiDi breaks.

**19. How do you handle secrets?**
Only from environment variables. In CI there is no fallback — the first pipeline run failed
precisely because a secret was missing, which is the behaviour I wanted.

### Results, honesty and judgement

**20. What bugs did the framework find that you didn't plant?**
Eight while building Phases 1–2 (in RESULTS.md with the run that found each): e.g. Pact
provider verification found inconsistent timestamp precision; security tests found URLs that
bypassed the error contract. Later the dashboard exposed a coverage-measurement bug in my own
framework: one test was resetting the JaCoCo agent and erasing other tests' coverage.

**21. What can't the framework tell you?**
Whether the visual baselines are right in the first place (a human approves them), anything
about AI output quality, and anything about systems it hasn't been pointed at. Performance
numbers come from shared CI runners, so they catch regressions, not capacity.

**22. What would you do differently?**
Start the measurement harness earlier — some Phase 1 tests would have been designed
differently had I seen the detection matrix first. And record per-test durations from the
start to balance shards.

**23. How did you make sure the numbers are real?**
Every figure in RESULTS.md has the run ID or commit, the machine, and the method. Where
something couldn't run (Edge, Docker Hub and Maven Central are blocked in my authoring
sandbox), the file says so and points to the CI run instead.

**24. How long does it take a new person to add a test?**
The guide's worked example is committed: two data rows (no code), two BDD example rows, one
eight-line API test. Running the module took 54 s for the API suite and 39 s for BDD on a
2-vCPU machine.

**25. Why should we believe you understand this rather than generated it?**
Ask me to change something live: add a seeded bug and predict which suites catch it, explain
why `RACE_ON_LAST_SLOT` only fails under concurrency, or walk through why the quarantine
filter had to treat parameterised tests as containers. The design decisions and their
alternatives are written down in DESIGN.md (D1–D45), including the ones that turned out wrong.
