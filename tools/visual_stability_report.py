#!/usr/bin/env python3
"""Aggregates visual-stability.yml results (<dir>/<browser>-<rep>.json) into a Markdown table:
per browser and check, how many runs matched, how many failed, and the largest differing and
anti-aliased pixel counts seen; then, per check, each distinct place the element was drawn
(sub-pixel position and viewport) with how many runs drew it there and how many of those failed,
because a sub-pixel move is a cause the images alone do not show.

usage: tools/visual_stability_report.py <dir>
"""
import glob
import json
import os
import sys
from collections import defaultdict


def summarise(results):
    rows = defaultdict(lambda: {"runs": 0, "failed": 0, "max_differing": 0, "max_ratio": 0.0,
                                "max_aa": 0, "runs_with_aa": 0})
    suites = defaultdict(lambda: {"runs": 0, "red": 0, "retaken": 0})
    for r in results:
        b = r["browser"]
        s = suites[b]
        s["runs"] += 1
        failed_checks = {f["check"] for f in r.get("visual_failures", [])}
        suite = r.get("suite") or {}
        if not suite or suite.get("failures") or suite.get("errors"):
            s["red"] += 1
        s["retaken"] += len(r.get("retaken", []))
        for check, c in r.get("checks", {}).items():
            row = rows[(b, check)]
            row["runs"] += 1
            row["failed"] += check in failed_checks
            row["max_differing"] = max(row["max_differing"], c["differing"])
            row["max_ratio"] = max(row["max_ratio"], c["ratio_pct"])
            row["max_aa"] = max(row["max_aa"], c["anti_aliased"])
            row["runs_with_aa"] += c["anti_aliased"] > 0
    return rows, suites


def positions(results):
    """(browser, check) -> {geometry without scrollY: [runs, failed]}."""
    out = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    for r in results:
        failed_checks = {f["check"] for f in r.get("visual_failures", [])}
        for check, c in r.get("checks", {}).items():
            g = c.get("geometry") or "not logged"
            g = g.split(" scrollY=")[0]
            cell = out[(r["browser"], check)][g]
            cell[0] += 1
            cell[1] += check in failed_checks
    return out


def main():
    d = sys.argv[1]
    results = [json.load(open(f)) for f in sorted(glob.glob(os.path.join(d, "*.json")))]
    if not results:
        print("no results")
        return 1
    rows, suites = summarise(results)
    print("# Visual stability\n")
    print("| browser | suite runs | red runs | captures retaken |")
    print("|---|---:|---:|---:|")
    for b, s in sorted(suites.items()):
        print(f"| {b} | {s['runs']} | {s['red']} | {s['retaken']} |")
    print("\n| browser | check | runs | visual failures | max differing px (max %) | runs with anti-aliased px | max anti-aliased px |")
    print("|---|---|---:|---:|---:|---:|---:|")
    for (b, check), r in sorted(rows.items()):
        print(f"| {b} | {check} | {r['runs']} | {r['failed']} | {r['max_differing']} ({r['max_ratio']:.4f} %) | "
              f"{r['runs_with_aa']} | {r['max_aa']} |")
    print("\n## Where each element was drawn\n")
    print("| browser | check | position and viewport | runs | failed |")
    print("|---|---|---|---:|---:|")
    for (b, check), cells in sorted(positions(results).items()):
        for g, (n, f) in sorted(cells.items()):
            print(f"| {b} | {check} | {g} | {n} | {f} |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
