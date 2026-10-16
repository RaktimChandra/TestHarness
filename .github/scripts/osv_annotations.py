#!/usr/bin/env python3
"""One GitHub annotation per package from OSV-Scanner's markdown table.

usage: osv_annotations.py <osv markdown output>
"""
import re
import sys


def ver(v):
    return [int(p) if p.isdigit() else p for p in re.split(r"[.\-]", v)]


def main():
    rows = {}
    for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
        c = [x.strip() for x in line.strip().strip("|").split("|")]
        if len(c) == 7 and c[0].startswith("https://osv.dev/"):
            r = rows.setdefault((c[2], c[3], c[4]), {"fixed": set(), "ids": [], "cvss": []})
            r["fixed"].add(c[5])
            r["ids"].append(c[0].rsplit("/", 1)[1])
            r["cvss"].append(float(c[1]) if re.match(r"^[\d.]+$", c[1]) else 0.0)
    lines = []
    for (eco, pkg, v), r in sorted(rows.items()):
        fixed = sorted((f for f in r["fixed"] if f and f != "--"), key=ver)
        lines.append(f"{eco} {pkg} {v} -> fixed in {fixed[-1] if fixed else 'no fix'}; "
                     f"{len(r['ids'])} advisories (max CVSS {max(r['cvss'])}): {' '.join(r['ids'])}")
    # GitHub shows at most 10 warnings per step, so the full list is also one error annotation.
    for line in lines[:9]:
        print(f"::warning title=OSV::{line}")
    if lines:
        print(f"::error title=OSV: {len(lines)} affected package(s)::" + "%0A".join(lines))
    print(f"{len(rows)} affected package(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
