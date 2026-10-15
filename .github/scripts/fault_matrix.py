#!/usr/bin/env python3
"""Combine per-bug fault-seeding results into the detection matrix (JSON + Markdown).

usage: fault_matrix.py <dir with <bug>.json files> <out-dir>
A suite "catches" a bug when, with the bug switched on, it has failures (or its build fails)
and it was green with no bug. A suite already red without a bug cannot be credited.
"""
import glob
import json
import os
import sys

ORDER = ["contract", "api", "security", "ui", "a11y", "visual", "bdd", "perf"]


def main():
    src, out = sys.argv[1], sys.argv[2]
    runs = {}
    for f in glob.glob(os.path.join(src, "*.json")):
        d = json.load(open(f))
        if "bug" in d:
            runs[d["bug"]] = d["suites"]
    baseline = runs.get("NONE", {})
    bugs = sorted(b for b in runs if b != "NONE")
    suites = [s for s in ORDER if any(s in runs[b] for b in runs)]
    matrix = {}
    for b in bugs:
        matrix[b] = {}
        for s in suites:
            r = runs[b].get(s)
            base = baseline.get(s)
            if r is None:
                cell = {"status": "not-run"}
            elif base is None:
                cell = {"status": "no-baseline", "failed": r["failed"], "tests": r["tests"]}
            elif base["detected"]:
                cell = {"status": "baseline-red", "failed": r["failed"], "tests": r["tests"]}
            else:
                cell = {"status": "caught" if r["detected"] else "escaped", "failed": r["failed"],
                        "tests": r["tests"], "exit_code": r["exit_code"], "seconds": r["seconds"],
                        "failures": r["failures"][:12]}
            matrix[b][s] = cell
    caught_by = {b: [s for s in suites if matrix[b][s]["status"] == "caught"] for b in bugs}
    rate = {s: (sum(1 for b in bugs if matrix[b][s]["status"] == "caught") / len(bugs)) if bugs else 0
            for s in suites}
    result = {"baseline": baseline, "bugs": bugs, "suites": suites, "matrix": matrix,
              "detection_rate_per_suite": rate, "caught_by": caught_by,
              "escaped_all": [b for b in bugs if not caught_by[b]]}
    os.makedirs(out, exist_ok=True)
    json.dump(result, open(os.path.join(out, "fault-seeding.json"), "w"), indent=2)

    lines = ["| Seeded bug | " + " | ".join(suites) + " |", "|---|" + "---|" * len(suites)]
    for b in bugs:
        cells = []
        for s in suites:
            c = matrix[b][s]
            if c["status"] == "caught":
                cells.append(f"**caught** {c['failed']}/{c['tests']}")
            elif c["status"] == "escaped":
                cells.append(f"escaped 0/{c['tests']}")
            else:
                cells.append(c["status"])
        lines.append(f"| `{b}` | " + " | ".join(cells) + " |")
    lines.append("| **detection rate** | " + " | ".join(f"{round(100 * rate[s])} %" for s in suites) + " |")
    base = ["", "Baseline (no bug): " + ", ".join(
        f"{s} {baseline[s]['failed']}/{baseline[s]['tests']} failed (exit {baseline[s]['exit_code']})"
        for s in suites if s in baseline)]
    detail = ["", "## Failing tests per bug", ""]
    for b in bugs:
        detail.append(f"### {b}")
        for s in suites:
            c = matrix[b][s]
            if c["status"] == "caught":
                for f in c["failures"]:
                    detail.append(f"- {s}: {f['test']} — {f['type']}: {f['message']}")
                if not c["failures"]:
                    detail.append(f"- {s}: build failed (exit {c['exit_code']}) without failed test cases")
        detail.append("")
    open(os.path.join(out, "fault-seeding.md"), "w").write("\n".join(lines + base + detail) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
