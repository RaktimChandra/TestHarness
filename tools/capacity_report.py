#!/usr/bin/env python3
"""Aggregates capacity-*.json files (tools/capacity.py) into capacity.md next to them.

usage: tools/capacity_report.py <dir>
Per flow: each runner's capacity, the spread between runners, and per level the median of
p95 / errors / completed flows / SUT CPU across runners.
"""
import glob
import json
import os
import statistics
import sys


def med(values):
    values = [v for v in values if v is not None]
    return round(statistics.median(values), 1) if values else None


def main():
    d = sys.argv[1]
    reports = [json.load(open(f)) for f in sorted(glob.glob(os.path.join(d, "capacity-*.json")))]
    if not reports:
        print("no capacity-*.json files")
        return 1
    out = ["# Capacity experiment", "",
           f"SUT limits: {reports[0].get('sut_cpus')} CPUs, "
           f"{(reports[0].get('sut_memory_bytes') or 0) / 2**30:.1f} GiB; {reports[0]['duration_s']} s per level. "
           f"A level keeps up when errors < {reports[0]['rules']['max_error_pct']} %, p95 <= "
           f"{reports[0]['rules']['p95_ms']} ms and completed flows >= "
           f"{int(reports[0]['rules']['min_achieved_ratio'] * 100)} % of the offered rate.", ""]
    for flow in ("full", "authenticated"):
        rs = [r for r in reports if r["flow"] == flow]
        if not rs:
            continue
        caps = [r["capacity_flows_per_s"] for r in rs]
        out += [f"## {flow} flow", "",
                f"Capacity per runner: {', '.join(f'{c:g}' for c in caps)} flows/s "
                f"(median {statistics.median(caps):g}, range {min(caps):g}–{max(caps):g}).", "",
                "| offered flows/s | runners | median p95 ms | median p99 ms | median errors % | median completed/s | median SUT CPU % | kept up |",
                "|---:|---:|---:|---:|---:|---:|---:|---|"]
        rates = sorted({l["offered_flows_per_s"] for r in rs for l in r["levels"]})
        for rate in rates:
            ls = [l for r in rs for l in r["levels"] if l["offered_flows_per_s"] == rate]
            kept = sum(1 for l in ls if l["keeps_up"])
            out.append(f"| {rate:g} | {len(ls)} | {med([l.get('p95_ms') for l in ls])} | {med([l.get('p99_ms') for l in ls])} | "
                       f"{med([l.get('error_pct') for l in ls])} | {med([l.get('completed_flows_per_s') for l in ls])} | "
                       f"{med([l.get('sut_cpu_pct_mean') for l in ls])} | {kept} of {len(ls)} |")
        out.append("")
    text = "\n".join(out) + "\n"
    open(os.path.join(d, "capacity.md"), "w").write(text)
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
