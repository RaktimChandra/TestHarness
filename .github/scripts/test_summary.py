#!/usr/bin/env python3
"""Summarise JUnit XML reports (surefire) for a CI job.

Counts <testcase> elements (not the testsuite header, which surefire reports as 0 for
@Nested classes with @DisplayName), writes a Markdown table to $GITHUB_STEP_SUMMARY and
emits one ::notice annotation so the numbers are visible through the checks API.

usage: test_summary.py <label> [glob ...]
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    label = sys.argv[1]
    patterns = sys.argv[2:] or ["**/target/surefire-reports/TEST-*.xml"]
    rows = {}
    failures = []
    skipped = []
    for pattern in patterns:
        for path in glob.glob(pattern, recursive=True):
            module = path.replace("\\", "/").split("/target/")[0] or "."
            r = rows.setdefault(module, {"tests": 0, "failed": 0, "errors": 0, "skipped": 0, "time": 0.0})
            root = ET.parse(path).getroot()
            for case in root.iter("testcase"):
                r["tests"] += 1
                r["time"] += float(case.get("time") or 0)
                bad = case.find("failure") if case.find("failure") is not None else case.find("error")
                if bad is not None:
                    msg = " ".join((bad.get("message") or bad.get("type") or "").split())[:400]
                    failures.append(f"{case.get('classname')} > {case.get('name')}: {msg}")
                if case.find("failure") is not None:
                    r["failed"] += 1
                elif case.find("error") is not None:
                    r["errors"] += 1
                elif case.find("skipped") is not None:
                    r["skipped"] += 1
                    why = " ".join((case.find("skipped").get("message") or "").split())[:160]
                    skipped.append(f"{case.get('classname', '').rsplit('.', 1)[-1]} > {case.get('name')}"
                                   + (f" ({why})" if why else ""))
    total = {k: sum(r[k] for r in rows.values()) for k in ("tests", "failed", "errors", "skipped", "time")}
    lines = [f"### {label}", "", "| module | tests | failed | errors | skipped | test time (s) |",
             "|---|---:|---:|---:|---:|---:|"]
    for module in sorted(rows):
        r = rows[module]
        lines.append(f"| {module} | {r['tests']} | {r['failed']} | {r['errors']} | {r['skipped']} | {r['time']:.1f} |")
    lines.append(f"| **total** | **{total['tests']}** | **{total['failed']}** | **{total['errors']}** | "
                 f"**{total['skipped']}** | **{total['time']:.1f}** |")
    text = "\n".join(lines) + "\n"
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text)
    print(text)
    compact = "; ".join(f"{m}={r['tests']}/{r['failed']}/{r['errors']}/{r['skipped']}" for m, r in sorted(rows.items()))
    print(f"::notice title={label} (tests/failed/errors/skipped)::total={total['tests']}/{total['failed']}/"
          f"{total['errors']}/{total['skipped']} test-time={total['time']:.1f}s | {compact}")
    # Which tests were skipped and why, so a skip count can be explained from the checks API.
    if skipped:
        listed = "; ".join(skipped)
        if len(listed) > 3800:
            listed = listed[:3800] + f" ... ({len(skipped)} in all)"
        print(f"::notice title={label} skipped tests::{listed}")
    # The failing tests themselves, one annotation each (GitHub shows at most ten per step).
    for f in failures[:8]:
        print(f"::error title={label} failed test::{f}")
    if len(failures) > 8:
        print(f"::error title={label}::{len(failures) - 8} more failing test(s), see the job summary")
    return 0


if __name__ == "__main__":
    sys.exit(main())
