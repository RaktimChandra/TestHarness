#!/usr/bin/env python3
"""Summarise the latest Gatling run (stats.json) for a CI job: per-request percentiles and errors."""
import glob
import json
import os
import sys


def main() -> int:
    label = sys.argv[1]
    runs = sorted(glob.glob(sys.argv[2] if len(sys.argv) > 2 else "**/target/gatling/*/js/stats.json", recursive=True),
                  key=os.path.getmtime)
    if not runs:
        print(f"::warning title={label}::no Gatling stats.json found")
        return 0
    data = json.load(open(runs[-1], encoding="utf-8"))
    rows = [("all requests", data["stats"])] + [(c["stats"]["name"], c["stats"]) for c in data["contents"].values()]
    lines = [f"### {label}", "", "| request | count | KO | p50 ms | p95 ms | p99 ms | max ms |",
             "|---|---:|---:|---:|---:|---:|---:|"]
    for name, s in rows:
        lines.append(f"| {name} | {s['numberOfRequests']['total']} | {s['numberOfRequests']['ko']} | "
                     f"{s['percentiles1']['total']} | {s['percentiles3']['total']} | {s['percentiles4']['total']} | "
                     f"{s['maxResponseTime']['total']} |")
    text = "\n".join(lines) + "\n"
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8").write(text)
    print(text)
    a = data["stats"]
    total = a["numberOfRequests"]["total"]
    ko = a["numberOfRequests"]["ko"]
    print(f"::notice title={label} gatling::requests={total} ko={ko} errorPct={100.0 * ko / max(total, 1):.2f} "
          f"p50={a['percentiles1']['total']} p95={a['percentiles3']['total']} p99={a['percentiles4']['total']} "
          f"max={a['maxResponseTime']['total']} rps={a['meanNumberOfRequestsPerSecond']['total']:.1f} | "
          + "; ".join(f"{n}:p95={s['percentiles3']['total']}" for n, s in rows[1:]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
