#!/usr/bin/env python3
"""Summarise a Gatling run from its console output (the final "Global Information" block).

Gatling 3.16 no longer writes stats.json and its simulation.log is binary, so the stable,
documented console summary is parsed instead.

usage: gatling_summary.py <label> <maven log file>
"""
import os
import re
import sys


def main() -> int:
    label, log = sys.argv[1], sys.argv[2]
    text = open(log, encoding="utf-8", errors="replace").read()
    blocks = text.split("---- Global Information")
    if len(blocks) < 2:
        print(f"::warning title={label}::no Gatling global summary in {log}")
        return 0
    block = blocks[-1].split("========")[0]
    values = {}
    for line in block.splitlines():
        # Gatling groups thousands with commas ("1,500").
        m = re.match(r"> (.+?)\s*\|\s*([\d.,]+)\s*\|\s*([\d.,]+|-)\s*\|\s*([\d.,]+|-)", line)
        if m:
            values[m.group(1).strip()] = (m.group(2).replace(",", ""), m.group(4).replace(",", ""))
    requests = {}
    tables = text.split("---- Requests ----")
    if len(tables) > 1:
        for line in tables[-1].split("\n\n")[0].splitlines():
            m = re.match(r"> (.+?)\s*\|\s*([\d,]+)\s*\|\s*([\d,]+)\s*\|\s*([\d,]+)", line)
            if m and m.group(1).strip() != "Global":
                requests[m.group(1).strip()] = (m.group(2).replace(",", ""), m.group(4).replace(",", ""))
    total, ko = values.get("request count", ("0", "0"))
    ko = "0" if ko == "-" else ko
    get = lambda k: values.get(k, ("?",))[0]
    rows = [("requests", total), ("failed (KO)", ko), ("p50 ms", get("response time 50th percentile (ms)")),
            ("p95 ms", get("response time 95th percentile (ms)")), ("p99 ms", get("response time 99th percentile (ms)")),
            ("max ms", get("max response time (ms)")), ("mean throughput rps", get("mean throughput (rps)"))]
    md = [f"### {label}", "", "| metric | value |", "|---|---:|"] + [f"| {k} | {v} |" for k, v in rows]
    md += ["", "| request | total | KO |", "|---|---:|---:|"] + [f"| {k} | {t} | {f} |" for k, (t, f) in requests.items()]
    out = "\n".join(md) + "\n"
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8").write(out)
    print(out)
    print(f"::notice title={label} gatling::" + " ".join(f"{k.replace(' ', '_')}={v}" for k, v in rows)
          + " | " + "; ".join(f"{k}={t}/{f}" for k, (t, f) in requests.items()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
