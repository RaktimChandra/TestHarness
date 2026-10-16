# PortSync adapter

Points TestHarness's API, contract, UI and BDD layers at
[PortSync](https://github.com/RaktimChandra/PortSync): a container-terminal operations system
in which a simulator publishes crane, berth, gate and yard events to Kafka and a Spring Boot core
applies them to PostgreSQL, raises SLA alerts and serves a React dashboard.

It shows the framework is not tied to its own demo app: nothing in `harness/` changed to
support a second, very different system (event-driven, no logins, no `data-testid`s).

## What it tests

| Suite | Class / feature | What it proves |
|---|---|---|
| Contract (offline, every build) | `SchemasTest` | The adapter's schema registry loads, covers every endpoint the clients call, and rejects a renamed field or an unknown enum value |
| Contract (live) | every live test | Every response from core and simulator is validated against `portsync-schemas/` in strict mode (`additionalProperties: false`), including the error body |
| API — differential | `ContainerNumberAgreementTest` | PortSync's ISO 6346 validator agrees with TestHarness's independent oracle on 300 generated numbers (all categories, remainder-ten, wrong digits); lookups give 400 with the right digit, and 404 for a valid never-seen number |
| API — consistency | `TerminalStateTest` | Yard invariants hold; overview utilisation equals the blocks' used ÷ capacity; block detail agrees with the summary; every stacked number is valid and unique; errors carry codes |
| Event handling | `EventHandlingTest` | Faults injected through the simulator, followed by event id through Kafka: wrong check digit → DLQ `INVALID_CHECK_DIGIT`; gate-out of a ghost → `CONTAINER_NOT_IN_YARD`, visible on that container; malformed record → `MALFORMED_EVENT`; back-dated → `IGNORED_STALE`; re-sent ids processed exactly once |
| Alert lifecycle | `AlertLifecycleTest` | Crane fault → `FAULTED` → `CRANE_FAULT_DURATION` alert opens → acknowledged → a second acknowledge is 409 → recover → the alert resolves itself (`AUTO`) |
| UI | `DashboardUiTest` | A KPI matches the API; the dashboard's live check-digit verdict agrees with the oracle (a third implementation); container lookup; acknowledging a live alert from the alerts page; axe-core on three pages |
| BDD | `features/portsync/*.feature` | The same rules in business language: check-digit examples including the remainder-ten case, normalised operator input, dead-lettering, stale events, the alert lifecycle |

Page objects use `Target`s led by element id and accessible name (PortSync has no test ids),
so the framework's honest self-healing applies to them too.

## Running it

The live tests need a running PortSync stack. PortSync needs Kafka, so it is started with its
own Compose file — the adapter never starts it, it only waits for it (core readiness, simulator
running, opening inventory processed).

**Terminal 1** (PowerShell, Git Bash or WSL — in a PortSync checkout):

```bash
git clone https://github.com/RaktimChandra/PortSync.git
cd PortSync
docker compose up -d --build
```

**Terminal 2** (in this repository):

```bash
./mvnw install -DskipTests -pl adapters/portsync -am     # once
./mvnw verify -pl adapters/portsync -Dportsync.live       # PowerShell: .\mvnw.cmd verify -pl adapters/portsync "-Dportsync.live"
```

Without `-Dportsync.live` only the offline contract tests run, so the default
`./mvnw verify` of the whole project never needs PortSync.

Configuration (`src/main/resources/harness.yml`), each overridable with
`-Dharness.portsync.<key>=...` or `HARNESS_PORTSYNC_<KEY>`:

| Key | Default |
|---|---|
| `portsync.api-url` | `http://localhost:8080` |
| `portsync.simulator-url` | `http://localhost:8081` |
| `portsync.ui-url` | `http://localhost:3000` |
| `portsync.ready-timeout` | `4m` |
| `portsync.min-events-processed` | `1500` |
| `portsync.alert-timeout` | `90s` |

In CI, `.github/workflows/portsync.yml` checks out PortSync, starts it with Compose and runs
everything above — on pull requests touching the adapter or the framework, weekly, and on
demand against a chosen PortSync ref.

## Limits

- The schemas are written from PortSync's source (`Views` records, commit `f34ac31`); they are
  the adapter's expectations, not a contract PortSync publishes.
- The alert tests depend on PortSync's 60× simulated clock (15 simulated minutes ≈ 15 s).
- Live tests share one stack and fault cranes, so they run one at a time.
