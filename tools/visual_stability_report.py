#!/usr/bin/env python3
"""Aggregates visual-stability.yml results (<dir>/<browser>-<rep>.json) into a Markdown table:
per browser and check, how many runs matched, how many failed, and the largest differing and
anti-aliased pixel counts seen.

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
    return 0


if __name__ == "__main__":
    sys.exit(main())
