# Capacity experiment

**Question:** how many booking flows per second does the Gate SUT sustain on fixed resources,
and what does signing in on every flow cost?

**Run:** GitHub Actions run 39419512861 (`.github/workflows/capacity.yml`), 2026-10-20, commit
`f5fa6a1`. Six jobs: two flows × three runners (`ubuntu-24.04`, 4 vCPU each).

## Method

- The SUT runs in Docker Compose with **2 CPUs and 1 GiB** for the app and 1 CPU / 512 MiB for
  PostgreSQL (`docker-compose.capacity.yml`), so the result means "on 2 CPUs", not "on whichever
  runner this was". Gatling runs on the same runner, outside those limits.
- `tools/capacity.py` steps an **open arrival rate** of flows per second; each level is one
  60-second Gatling run of `CapacitySimulation`. `docker stats` samples the app's CPU while it
  runs (100 % = one CPU, so 200 % is the limit).
- Two flows that differ only by the password check: **full** = sign in, list slots, book, read,
  cancel; **authenticated** = the same with a token obtained once.
- A level **keeps up** when errors < 1 %, p95 ≤ 800 ms (the smoke test's gate) and completed
  flows ≥ 95 % of the offered rate. Capacity = the highest level that keeps up; stepping stops
  after two consecutive levels that do not.
- Per-runner JSON, Maven/Gatling logs and the containers' state at the end are in this
  directory; `capacity.md` is the generated table (`tools/capacity_report.py`).

## Results

| Flow | Capacity, runner 1 / 2 / 3 | Median | At the last level kept up | First level that did not |
|---|---|---:|---|---|
| full (sign-in on every flow) | **25 / 30 / 20** flows/s | **25** | p95 220–589 ms, SUT CPU 138–152 % | p95 3.2–7.3 s, 0 % errors — latency, not errors, breaks first |
| authenticated (token reused) | **200 / 200 / 200** flows/s | **200** | p95 8–31 ms, SUT CPU 71–75 % | 240/s: p95 60 s (the request timeout), **29.5–35.5 % errors**, SUT CPU only ~97 % |

Full tables per level: [capacity.md](capacity.md).

## What this shows

- **Signing in costs about 8× the capacity** (median 25 vs 200 flows/s). In the full flow the
  app's CPU climbs with the rate (45 % at 5/s, 131 % at 20/s, 152 % at 25/s): bcrypt on every
  sign-in is the work. When it saturates, latency goes first — p95 jumps from 0.4 s to 7 s
  between 25 and 30 flows/s while errors stay at 0 %.
- **The authenticated flow hits a different wall, and it is not CPU.** At 240 flows/s the app
  used about 97 % of its 200 % CPU, yet requests timed out. Its log at the end of that level, in
  all three runs: `HikariPool-1 - Connection is not available ... (total=20, active=20, idle=0,
  waiting≈5,600)`. The 20-connection pool (`spring.datasource.hikari.maximum-pool-size`) and the
  database behind it are the limit there.
- **The runners disagree by up to 50 %** on the full flow (20 to 30). Same limits, same code:
  the noise of shared hosts. One runner's number would have been misleading; the authenticated
  flow, far from its CPU limit, agreed exactly.

## Limits

- The load generator shares the runner with the SUT (the SUT's own CPU is capped, the host's
  other two vCPUs are shared by PostgreSQL and Gatling), so at high rates Gatling itself may
  contribute latency.
- Levels are 60 s; a longer soak could show slower failure modes (memory, pool leaks).
- The containers' logs are the last 60 lines after the final level, so they show the end state
  of the overload, not when it began.
- The first run of this workflow (39410838330, on `main`) is not used: its Gatling parser read
  "1,500" as 0 requests, so every level was marked as not keeping up. The fix and its test are
  in `tools/capacity.py` and `tools/tests/`.
