# TestHarness on CIForge

**Run:** `20261016-053049-a47fe6`, 2026-10-16, on the 2-vCPU authoring sandbox, with
[CIForge](https://github.com/RaktimChandra/CIForge) at commit `2ef5457` and TestHarness at
`cff831e` (branch `phase5`, the commit that added `.ciforge.yml`).

```bash
ciforge run .ciforge.yml --source /path/to/TestHarness --branch phase5 \
  -p mvn="mvn -o -B" \
  -p suite.args="-Dui.node=system -Dharness.ui.binary.chrome=... -Dharness.ui.driver-path.chrome=... -Dharness.ui.no-sandbox=true"
```

The two parameters are the sandbox's only differences from the defaults: Maven runs offline
(Maven Central is blocked there) and Chrome is a local binary. CIForge mirrored the repository,
checked out the commit into a fresh workspace per build, and ran the chain with
`max_parallel: 2`.

## Result: success, 8 min 11 s end to end

| Build | Depends on | Started (UTC) | Finished | Result | What ran |
|---|---|---|---|---|---|
| build | — | 05:30:49 | 05:33:35 | success | SUT, framework and contract tests; install |
| api | build | 05:33:37 | 05:35:40 | success | 109 tests, 0 failed |
| bdd | build | 05:33:37 | 05:35:11 | success | 20 tests, 0 failed |
| ui-1 | build | 05:35:13 | 05:36:57 | success | shard 1/2: 15 tests, 0 failed, 3 skipped (visual, see RESULTS §7) |
| ui-2 | build | 05:35:41 | 05:37:47 | success | shard 2/2: 14 tests, 0 failed, 2 skipped |
| mutation | build | 05:36:58 | 05:38:10 | success | 143 mutants, 143 killed (100 %) |
| perf | build | 05:37:48 | 05:38:59 | success | p95 142 ms, p99 298 ms (gates 800 / 1500 ms) |
| quarantined | build | 05:38:12 | 05:38:57 | success | the 1 quarantined test ran and passed this time (non-blocking either way) |
| quality-gate | api, bdd, ui-1, ui-2, mutation, perf | 05:38:59 | 05:39:00 | success | collected the suites' reports as artifacts: **158 tests, 0 failed, 5 skipped** |

- The two UI shards together ran **29** UI tests (15 + 14) — the same set as the unsharded
  suite in RESULTS §23 — so the hash split lost or duplicated nothing.
- The Gatling latencies are higher than in a solo run (§23: p95 96 ms) because two builds
  share two vCPUs (`max_parallel: 2`); the gates still pass with wide margins.
- The quality gate received the suite reports through CIForge artifact dependencies
  (`artifacts_from`), not through a shared disk.

## Files

- `run-20261016-053049-a47fe6.json` — the run, every build and step with timestamps and exit
  codes, as stored in CIForge's SQLite database, plus the key lines of each build log.
- `quality-gate.log` — the gate's own log.

## In GitHub Actions — `ciforge.yml`, 2026-10-20

`ciforge.yml` installs CIForge from its repository on a GitHub runner and runs `.ciforge.yml`
against the pushed commit, with the quality gate in CIForge's **Docker runner**
(`python:3.12-slim`). The run record is exported by `tools/ciforge_export.py` and pushed to a
`ciforge/<run id>` branch; both runs are copied here.

| Actions run | CIForge run | Commit | Result | Wall-clock | Notes |
|---|---|---|---|---|---|
| 39410844981 | `20261020-105132-7c7e70` | `768ee4d` (main) | **failure** | — | both UI shards failed: with `HARNESS_ENV=ci` the visual checks require a baseline, and they looked for `chrome-linux`, which does not exist. The quality gate was cancelled, as designed. |
| 39419521879 | `20261020-120450-36aafa` | `f5fa6a1` | **success**, 9 of 9 builds | **5 min 25 s** (12:04:50 → 12:10:15) | `-p suite.args=-Dharness.visual.platform=chrome-ci`: this runner renders like the `ci.yml` UI jobs |

Second run, from the run record: API 112 tests, BDD 24 (1 skipped: the drafted-step report),
UI 15 + 14 with the visual checks **run and passed** (they were skipped in the sandbox run
above), PIT 143 of 143 mutants killed, Gatling and the quarantined test green, quality gate
green in its container. It is faster than the sandbox run (8 min 11 s) because the runner has
4 vCPUs to the sandbox's 2.

The failed first run is kept on purpose: it is the gate doing its job on a configuration
mistake.
