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
> In CI I switch each bug on and run every suite type against it — three times, because one
> run of a noisy thing isn't a measurement. The API suite caught all six every time; BDD five;
> contract and UI two. Security caught two in one run and one in the other two, and Gatling
> caught the check-digit bug in two runs of three through its error-rate gate: both depend on
> random container numbers hitting the broken case.
> The race was caught by **one test out of 151**. And one of my own strategy assumptions was
> wrong: I expected Pact to catch the timezone bug, and it can't, because it matches
> timestamps by format. I kept that in the docs.
>
> On top of that: flakiness is measured from run history — a test that flips pass/fail on
> the same commit is scored and quarantined into a non-blocking job, never retried until
> green. Self-healing locators exist, but every heal is reported with the exact fix. Mutation
> testing took the domain rules from 95.1 % to 100 %. Test impact analysis caught the bug in
> 18 of 18 selected runs — but saved only 4–33 % of the time, and I explain why.
>
> Everything is one command: `./mvnw verify`, 526 JVM tests in six and a half minutes on a
> 2-vCPU machine. CI runs it across Chrome, Firefox and Edge, sharded by recorded duration, on
> Selenium Grid, with a single quality gate, plus OSV and ZAP scans and a Windows job. The same
> pipeline runs on a real TeamCity server and on CIForge, my own CI orchestrator. And to show
> it isn't tied to my demo app, the same framework tests PortSync — a Kafka event-processing
> system I also built — with 54 tests in CI, where it found two real contrast defects.

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
Over three recordings, the selected subset caught the bug 18 times out of 18, but saved only
3.8–33.2 % of wall-clock, and the same bug moved by up to 16 points between runs. Most tests go through the same booking service, and start-up (JVM, Spring,
PostgreSQL) is a fixed cost. It pays off for narrow changes. The map also isn't stable —
two recordings of the same code selected 83 and 81 tests for one file, because random test
data takes different paths. So CI re-records it on every push to main, and on PRs it orders
tests to fail fast; it never skips them.

### Flakiness, healing, AI

**10. How do you define flaky?**
Flips between pass and fail on consecutive runs of the **same commit**, divided by the number
of transitions. Pass on one commit and fail on the next is a regression, not flakiness. In
three repeats of 20 CI runs each, the demo test scored 0.368, 0.474 and 0.421 and was
quarantined every time; its stable twin scored 0 every time. Its failure rate moved from 25 %
to 45 %, which is why the score uses flips rather than the failure rate.

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
the source tree. I measured it with three small open models served by Ollama in CI, on eight
user stories, twice each — scored by how many drafted steps an existing step definition
already runs, matched the way Cucumber matches. qwen2.5 7B: 100 % valid Gherkin and 68.5 % step
reuse (69.9 % in a second run); the 1.5B and 3B models: 88 % and 69 % valid, 43 % and 36 %
reuse. Only one 7B draft per run ran with no new glue code. And asked to explain the failures of a seeded capacity bug, the two
smallest models mostly blamed the tests or the environment — so drafts and explanations are a
starting point for a human, which is exactly how the tool labels them. I haven't measured a large hosted model.

### CI/CD and cloud

**14. Walk me through the pipeline.**
Build once with framework and SUT tests; then API, BDD and UI suites in parallel — UI across
Chrome, Firefox and Edge, two shards each — plus Compose smoke, Selenium Grid, Gatling and PIT.
Quarantined tests run in a non-blocking job. One quality-gate job is the required check: green
only if every blocking job succeeded, and "skipped" counts as not passed.

**15. How does sharding work?**
Longest test first onto the currently lightest shard (LPT), using mean durations that the
`publish` job records on the `ci-history` branch after every run on main. Every shard computes
the same plan from the same file, so there's no coordinator. A test without a recorded
duration — a new one — falls back to a stable CRC32 hash, so a stale file can never lose or
duplicate a test. Before any history exists, it's all hash; RESULTS shows the before and after.

**16. You wrote a TeamCity config — did you run it?**
Yes. A workflow starts a TeamCity 2025.07 server and an agent image I built (JDK 21, Docker,
Chrome), turns on versioned settings in Kotlin over REST, and the server itself imports and
compiles `settings.kts` and runs the chain: build 350 tests, API 112, BDD 24, UI in two shards,
Gatling, PIT — all green. The first two runs failed only on visual checks: the agent renders
fonts differently from the runner that made the baselines, so baselines are now per agent
platform. That's the kind of thing you only learn by running it.

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
framework: one test was resetting the JaCoCo agent and erasing other tests' coverage. In
Phase 6: 41 known vulnerabilities in transitive dependencies, six missing security headers
(ZAP), two contrast defects in PortSync — and several bugs in my own tooling that only real
runs exposed: a Gatling parser that read "1,500" as zero, a scanner that stored axe's target
list as a string so known issues never matched, and re-runs that doubled history.

**21. What can't the framework tell you?**
Whether the visual baselines are right in the first place (a human approves them), and
anything about systems it hasn't been pointed at. The AI evaluation measures how much of a
draft runs on existing steps, not whether it tests the right thing. Performance smoke numbers
come from shared CI runners, so they catch regressions; the capacity experiment pins the SUT
to 2 CPUs and 1 GiB so its numbers mean something, but only for that box.

**22. What would you do differently?**
Start the measurement harness earlier — some Phase 1 tests would have been designed
differently had I seen the detection matrix first. Repeat every experiment from the start: my
first fault-seeding run said Gatling caught nothing, and the repeats showed that held for one
run only. And record per-test durations from day one to balance shards.

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
alternatives are written down in DESIGN.md (D1–D65), including the ones that turned out wrong.
