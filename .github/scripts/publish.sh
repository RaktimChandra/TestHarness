#!/usr/bin/env bash
# Publish job (main only): persists this run's test history on the ci-history branch, derives
# shard durations, the flakiness leaderboard and the quarantine proposal from all recorded
# runs, then builds the dashboard, Allure report and blog into a site pushed to gh-pages.
#
# Inputs (prepared by the workflow): $RUNNER_TEMP/inputs/*.tgz (dashboard-inputs artifacts),
# $RUNNER_TEMP/allure (Allure report, optional), $RUNNER_TEMP/tia (impact map, optional).
# Needs: the CLI built (./th), GH_TOKEN for the gh-pages push.
set -euo pipefail
ROOT=$(pwd)
T="$RUNNER_TEMP"
H="$T/ci-history"
SITE="$T/site"
KEEP_RUNS=${KEEP_RUNS:-120}
SHORT=${GITHUB_SHA::7}
summary() { echo "$*" >> "$GITHUB_STEP_SUMMARY"; }

git config --global user.name "github-actions[bot]"
git config --global user.email "41898282+github-actions[bot]@users.noreply.github.com"

# --- 1. the ci-history branch ------------------------------------------------------------
if git ls-remote --exit-code --heads origin ci-history >/dev/null 2>&1; then
  git fetch --depth=1 origin ci-history
  git worktree add -B ci-history "$H" FETCH_HEAD
else
  git worktree add --detach "$H"
  (cd "$H" && git checkout -q --orphan ci-history && git rm -rfq . && git clean -fdxq)
fi

RUN_DIR="$H/runs/$(date -u +%Y%m%dT%H%M%SZ)-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
mkdir -p "$RUN_DIR"
UNPACK="$T/unpacked"
mkdir -p "$UNPACK"
shopt -s nullglob
for tgz in "$T"/inputs/*.tgz; do
  name=$(basename "$tgz" .tgz); name=${name#dashboard-inputs-}
  mkdir -p "$UNPACK/$name"
  tar -xzf "$tgz" -C "$UNPACK/$name"
done
python3 "$ROOT/.github/scripts/merge_inputs.py" "$UNPACK" "$ROOT" "$RUN_DIR"

# Oldest runs beyond the window are dropped: history is a sliding window, not an archive.
ls -1d "$H"/runs/*/ | sort | head -n -"$KEEP_RUNS" | xargs -r rm -rf

./th history durations --jsonl "$H/runs" --out "$H/shard-durations.json" > "$T/durations.txt"
./th history durations --jsonl "$H/runs" --suite ui-suite --shards 2 --out "$T/ui-durations.json" >> "$T/durations.txt"
./th history flaky --jsonl "$H/runs" --top 25 > "$H/flaky.txt"
./th history quarantine --jsonl "$H/runs" > "$H/quarantine-proposal.txt"
[ -f "$T/tia/test-impact-map.json" ] && cp "$T/tia/test-impact-map.json" "$H/test-impact-map.json"
RUNS=$(ls -1d "$H"/runs/*/ | wc -l)
cat > "$H/README.md" <<MD
# ci-history

Written by the \`publish\` job of [ci.yml](https://github.com/$GITHUB_REPOSITORY/blob/main/.github/workflows/ci.yml)
after every pipeline on \`main\`; never edited by hand.

| File | What it is | Used by |
|---|---|---|
| \`runs/<time>-<run id>-<attempt>/\` | the JSON Lines test history of one pipeline, per job | everything below; the dashboard |
| \`shard-durations.json\` | mean duration per test over the recorded runs | UI shards (\`-Dharness.shard.durations\`) |
| \`test-impact-map.json\` | which SUT files each test executed (recorded on main) | the \`impacted\` job on pull requests |
| \`flaky.txt\` | flakiness leaderboard over the recorded runs | people |
| \`quarantine-proposal.txt\` | what the quarantine policy would list today | a reviewer, before editing \`quarantine.json\` |

Window: the newest $KEEP_RUNS runs. Now: $RUNS run(s); last update from run
[$GITHUB_RUN_ID](https://github.com/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID) on commit \`$SHORT\`.
MD
(
  cd "$H"
  git add -A
  git commit -qm "history: run $GITHUB_RUN_ID ($SHORT)"
  for i in 1 2 3; do git push -q origin HEAD:ci-history && break; sleep $((i * 5)); done
)
summary "### Published history"
summary "- ci-history: $RUNS run(s) in the window"
summary '```'
cat "$T/durations.txt" >> "$GITHUB_STEP_SUMMARY"
head -8 "$H/flaky.txt" >> "$GITHUB_STEP_SUMMARY"
cat "$H/quarantine-proposal.txt" >> "$GITHUB_STEP_SUMMARY"
summary '```'

# --- 2. the site -----------------------------------------------------------------------------
mkdir -p "$SITE"
./th dashboard --jsonl "$H/runs" --out "$SITE/dashboard" | tee -a "$T/dashboard.txt"
if [ -d "$T/allure" ] && [ -f "$T/allure/index.html" ]; then cp -r "$T/allure" "$SITE/allure"; fi
python3 "$ROOT/.github/scripts/build_site.py" "$ROOT" "$SITE" "$RUNS"
touch "$SITE/.nojekyll"
(
  cd "$SITE"
  git init -q -b gh-pages
  git add -A
  git commit -qm "site: run $GITHUB_RUN_ID ($SHORT)"
  git push -q --force "${PAGES_REMOTE:-https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git}" HEAD:gh-pages
)
summary "- gh-pages: dashboard, Allure report and blog for $SHORT"
