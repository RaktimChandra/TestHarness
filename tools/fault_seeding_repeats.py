#!/usr/bin/env python3
"""Compares repeated fault-seeding runs (fault-seeding.yml): for every (bug, suite) pair, in how
many runs the suite caught the bug, and how many tests failed each time.

usage: tools/fault_seeding_repeats.py experiments/fault-seeding/runs/*.json > experiments/fault-seeding/repeats.md
"""
import json
import os
import sys


def main():
    runs = []
    for path in sys.argv[1:]:
        runs.append((os.path.splitext(os.path.basename(path))[0], json.load(open(path))))
    suites = runs[0][1]["suites"]
    bugs = list(runs[0][1]["matrix"])
    n = len(runs)
    out = [f"# Fault seeding, repeated ({n} runs)", "",
           "Runs (CI run IDs): " + ", ".join(f"`{r}`" for r, _ in runs) + ". A cell is *caught in k of "
           f"{n} runs* with the failed-test counts per run; **bold** marks a pair whose verdict differed "
           "between runs.", "",
           "| bug | " + " | ".join(suites) + " |", "|---|" + "---|" * len(suites)]
    unstable = []
    for bug in bugs:
        cells = []
        for s in suites:
            vals = [d["matrix"][bug][s] for _, d in runs]
            caught = sum(1 for v in vals if v["status"] == "caught")
            counts = "/".join(str(v["failed"]) for v in vals)
            cell = f"{caught}/{n} ({counts})" if caught else "–"
            if 0 < caught < n:
                cell = f"**{cell}**"
                unstable.append((bug, s, caught))
            cells.append(cell)
        out.append(f"| {bug} | " + " | ".join(cells) + " |")
    out += ["", "Baselines (no bug): every suite green in every run: " +
            ("yes" if all(not v["detected"] for _, d in runs for v in d["baseline"].values()) else "NO"), ""]
    if unstable:
        out.append("Verdicts that changed between runs:")
        out += [f"- {b} × {s}: caught in {k} of {n}" for b, s, k in unstable]
    else:
        out.append("Every verdict was the same in every run.")
    out.append("")
    rates = {s: [d["detection_rate_per_suite"][s] for _, d in runs] for s in suites}
    out += ["Detection rate per suite (bugs caught of 6), per run: " +
            "; ".join(f"{s} {'/'.join(str(round(r * 6)) for r in v)}" for s, v in rates.items()), "",
            "A cell with failed count 0 but caught (perf) means the suite failed on its gate (Gatling's error-rate "
            "assertion), not on a test.", ""]
    print("\n".join(out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
