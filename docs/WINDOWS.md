# Running TestHarness on Windows 11

Step by step, **one command at a time**. Each step says which terminal window to use.
All commands are for **PowerShell** (Windows Terminal → PowerShell).

## 0. One-time prerequisites

| Tool | Why | Check (Terminal A) |
|---|---|---|
| JDK 21 (e.g. Temurin 21) | builds and runs everything | `java -version` → `21.x` |
| Git | clone the repo | `git --version` |
| Docker Desktop (WSL2 backend), **running** | PostgreSQL for the tests (Testcontainers) and the Compose stack | `docker info` |
| Google Chrome | UI suite default browser | open Chrome once |
| Firefox / Edge (optional) | cross-browser runs | — |

You do **not** need Maven or Node.js: the Maven Wrapper downloads Maven, and the UI build
downloads its own Node.js into `sut\gate-ui\target`.

> If `docker info` fails, start Docker Desktop and wait until it says "Engine running".

## 1. Get the code — Terminal A

```powershell
git clone https://github.com/RaktimChandra/TestHarness.git
```

```powershell
cd TestHarness
```

## 2. Run everything (the one command) — Terminal A

Builds the SUT and framework, then runs the unit, framework, API, UI (Chrome) and BDD suites.
The SUT and PostgreSQL are started automatically.

```powershell
.\mvnw.cmd verify
```

Expected: `BUILD SUCCESS`. The first run downloads dependencies and takes longer.

## 3. Open the test report — Terminal A

```powershell
.\mvnw.cmd -N -Preport verify
```

The report loads its data over HTTP, so serve it with the web server that ships with the JDK:

```powershell
jwebserver -d "$PWD\target\allure-report" -p 8000
```

Open http://localhost:8000 in a browser. Stop the server with Ctrl+C.

## 4. Run one suite or one browser — Terminal A

API suite only:

```powershell
.\mvnw.cmd verify -pl suites/api-suite -am
```

UI suite in Firefox (or `edge`):

```powershell
.\mvnw.cmd verify -pl suites/ui-suite -am "-Dharness.ui.browser=firefox"
```

Watch the browser instead of running headless:

```powershell
.\mvnw.cmd verify -pl suites/ui-suite -am "-Dharness.ui.headless=false"
```

Only `@smoke` BDD scenarios:

```powershell
.\mvnw.cmd verify -pl suites/bdd-suite -am "-Dcucumber.filter.tags=@smoke"
```

## 5. Run the SUT in Docker and test it from outside

**Terminal B** — start the app and database (leave it running):

```powershell
docker compose up --build
```

Wait for `Started GateApplication`. The UI is now at http://localhost:8080
(users `driver1` / `clerk1`, password `gate-demo-pass`).

**Terminal A** — point the API suite at that container:

```powershell
$env:HARNESS_ENV = "docker"
```

```powershell
.\mvnw.cmd verify -pl suites/api-suite -am
```

```powershell
Remove-Item Env:HARNESS_ENV
```

**Terminal B** — stop with Ctrl+C, then remove the containers and data:

```powershell
docker compose down -v
```

## 6. Run the UI suite on Selenium Grid (Docker) — Terminals A and B

**Terminal B** — start the app, database, Grid hub and Chrome/Firefox/Edge nodes, and wait
until their health checks pass (the first start downloads the browser images and takes a
while):

```powershell
docker compose -f docker-compose.yml -f docker-compose.grid.yml up -d --build --wait --wait-timeout 400
```

The Grid UI is at http://localhost:4444 — it should list three nodes; under *Sessions* you
can watch a running browser. To follow the logs in this window:

```powershell
docker compose -f docker-compose.yml -f docker-compose.grid.yml logs -f
```

**Terminal A** — tell the framework where the app and the Grid are:

```powershell
$env:HARNESS_ENV = "docker"
```

```powershell
.\mvnw.cmd verify -pl suites/ui-suite -am "-Dharness.ui.grid-url=http://localhost:4444" "-Dharness.sut.browser-base-url=http://gate:8080" "-DexcludedGroups=visual" "-Dharness.ui.browser=firefox"
```

(`browser-base-url` is the address the browsers *inside Docker* use; visual checks are
excluded because their baselines are rendered on the CI runner, not in the node images.)

```powershell
Remove-Item Env:HARNESS_ENV
```

**Terminal B** — stop following the logs with Ctrl+C, then remove everything:

```powershell
docker compose -f docker-compose.yml -f docker-compose.grid.yml down -v
```

## 7. Performance smoke test (Gatling) — Terminal A

Runs as part of `verify`. On its own, with the default budget (60 users over 30 s):

```powershell
.\mvnw.cmd verify -pl suites/perf-suite -am
```

With a bigger load and a stricter gate:

```powershell
.\mvnw.cmd verify -pl suites/perf-suite -am "-Dperf.users=200" "-Dperf.ramp=60" "-Dperf.p95=500"
```

Open the newest `suites\perf-suite\target\gatling\bookingflowsimulation-*\index.html` in a
browser. To build everything *except* the load test, add `"-DskipPerf"` to any command.

## 8. Accessibility and visual results — Terminal A

After a UI run, axe-core results are in `suites\ui-suite\target\a11y\*.json` and in the
Allure report. Visual checks compare against the CI baselines; on your machine they write
*candidates* to `suites\ui-suite\target\visual\new\` and are reported as skipped — that
is expected. To check your own machine's rendering is stable, create local baselines once:

```powershell
.\mvnw.cmd verify -pl suites/ui-suite -am "-Dtest=VisualRegressionTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dharness.visual.mode=update"
```

and run the suite again: the five checks now compare and should pass. Do not commit those
PNGs; committed baselines come from the `visual-baselines` workflow (FRAMEWORK_GUIDE.md).

## 9. Contract tests (Pact) — Terminal A

The UI build regenerates the consumer contract; the API build verifies it:

```powershell
.\mvnw.cmd verify -pl sut/gate-ui,sut/gate-api -am
```

```powershell
git status contracts
```

If `git status` shows `contracts\pacts\gate-ui-gate-api.json` as modified, you changed what
the UI sends or expects — commit the file with your change (CI fails if they differ).

## 10. The `th` command — Terminal A

Install the framework once, so single suites can run on their own (`th.cmd` also builds
itself on first use):

```powershell
.\mvnw.cmd install -DskipTests
```

Then use `th.cmd` from the repository root. Show every command:

```powershell
.	h.cmd --help
```

Run the UI suite in Firefox, smoke tests only:

```powershell
.	h.cmd run --suite ui --browser firefox --tags smoke
```

Re-run only what failed last time:

```powershell
.	h.cmd rerun-failed
```

Generate and open the Allure report (stop with Ctrl+C):

```powershell
.	h.cmd report
```

## 11. Flakiness leaderboard and quarantine — Terminal A

Every test run records results under each module's `target	est-history`. Show the tests
that flip between pass and fail on the same commit:

```powershell
.	h.cmd history flaky --jsonl suites
```

The committed results of the 20-run CI demo show a real flaky test:

```powershell
.	h.cmd history flaky --jsonl experimentslaky-demo\history.jsonl
```

## 12. The quality dashboard — Terminal A

Run something first (step 2 or step 4), then build the dashboard from those results and open
it in your default browser:

```powershell
.	h.cmd dashboard --open
```

It is written to `target\dashboard\index.html` and opens straight from disk. Panels with no
data say what to run to fill them.

## 13. Test PortSync with the adapter — Terminals B and A

Needs about 6 GB free for Docker (Kafka, PostgreSQL and three PortSync containers).

**Terminal B** — get PortSync (in any folder outside TestHarness):

```powershell
git clone https://github.com/RaktimChandra/PortSync.git
```

```powershell
cd PortSync
```

```powershell
docker compose up -d --build
```

The first build takes several minutes. Leave Terminal B open.

**Terminal A** — in TestHarness (after step 10's install):

```powershell
.\mvnw.cmd verify -pl adapters/portsync "-Dportsync.live"
```

The tests wait until PortSync is ready (up to 4 minutes), then run the API, contract, event,
alert, UI, accessibility and BDD suites. When finished, in **Terminal B**:

```powershell
docker compose down -v
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop is not running, or WSL2 integration is off (Settings → Resources → WSL integration). |
| `client version ... is too old` from Docker | Update Docker Desktop. (Testcontainers 1.21.4, used here, was verified against Docker Engine 29.8.) |
| UI tests fail to start a browser | Install/update Chrome; Selenium Manager downloads the matching driver automatically. |
| Port 8080 or 5432 busy (Compose) | Stop the other program, or change the left-hand port in `docker-compose.yml`. |
| PortSync tests: "PortSync is not running or not ready" | Check Terminal B: `docker compose ps` should list `portsync-core`, `simulator`, `frontend`, `kafka`, `postgres` as running/healthy. |
| Need to reproduce a run's random data | Use the seed printed in the log: `"-Dharness.data.seed=<seed>"`. |
| Grid run: `ERR_CONNECTION_REFUSED` in the browser | `-Dharness.sut.browser-base-url=http://gate:8080` is missing — browsers in Docker cannot use `localhost:8080`. |
| Grid run: "Browser console capture unavailable" | The nodes must advertise `http://localhost:4444` (`SE_NODE_GRID_URL`, set in `docker-compose.grid.yml`); if your Grid is elsewhere set `GRID_PUBLIC_URL` before `docker compose up`. |
