#!/usr/bin/env python3
"""Summarise axe-core scans (target/a11y/*.json) per page and impact for a CI job."""
import glob
import json
import os
import sys

IMPACTS = ["critical", "serious", "moderate", "minor"]


def main() -> int:
    label = sys.argv[1]
    files = sorted(glob.glob(sys.argv[2] if len(sys.argv) > 2 else "**/target/a11y/*.json", recursive=True))
    rows = []
    for f in files:
        r = json.load(open(f, encoding="utf-8"))
        counts = {i: 0 for i in IMPACTS}
        rules = []
        for v in r["violations"]:
            counts[v.get("impact") or "minor"] += 1
            rules.append(f"{v['rule']}({v['impact']},{v['nodes']})")
        rows.append((r["page"], r["browser"], counts, rules))
    lines = [f"### {label}", "", "| page | browser | critical | serious | moderate | minor | rules |",
             "|---|---|---:|---:|---:|---:|---|"]
    for page, browser, c, rules in rows:
        lines.append(f"| {page} | {browser} | {c['critical']} | {c['serious']} | {c['moderate']} | {c['minor']} | "
                     f"{', '.join(rules) or '—'} |")
    text = "\n".join(lines) + "\n"
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8").write(text)
    print(text)
    total = {i: sum(r[2][i] for r in rows) for i in IMPACTS}
    detail = "; ".join(f"{p}:{'/'.join(str(c[i]) for i in IMPACTS)}" for p, _, c, _ in rows)
    print(f"::notice title={label} a11y (critical/serious/moderate/minor)::pages={len(rows)} "
          f"total={'/'.join(str(total[i]) for i in IMPACTS)} | {detail}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
