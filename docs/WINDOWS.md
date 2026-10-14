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

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop is not running, or WSL2 integration is off (Settings → Resources → WSL integration). |
| `client version ... is too old` from Docker | Update Docker Desktop. (Testcontainers 1.21.4, used here, was verified against Docker Engine 29.8.) |
| UI tests fail to start a browser | Install/update Chrome; Selenium Manager downloads the matching driver automatically. |
| Port 8080 or 5432 busy (Compose) | Stop the other program, or change the left-hand port in `docker-compose.yml`. |
| Need to reproduce a run's random data | Use the seed printed in the log: `"-Dharness.data.seed=<seed>"`. |
