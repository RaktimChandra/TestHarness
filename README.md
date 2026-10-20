# ci-history

Written by the `publish` job of [ci.yml](https://github.com/RaktimChandra/TestHarness/blob/main/.github/workflows/ci.yml)
after every pipeline on `main`; never edited by hand.

| File | What it is | Used by |
|---|---|---|
| `runs/<time>-<run id>-<attempt>/` | the JSON Lines test history of one pipeline, per job | everything below; the dashboard |
| `shard-durations.json` | mean duration per test over the recorded runs | UI shards (`-Dharness.shard.durations`) |
| `test-impact-map.json` | which SUT files each test executed (recorded on main) | the `impacted` job on pull requests |
| `flaky.txt` | flakiness leaderboard over the recorded runs | people |
| `quarantine-proposal.txt` | what the quarantine policy would list today | a reviewer, before editing `quarantine.json` |

Window: the newest 120 runs. Now: 2 run(s); last update from run
[39410820490](https://github.com/RaktimChandra/TestHarness/actions/runs/39410820490) on commit `768ee4d`.
