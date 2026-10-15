#!/usr/bin/env python3
"""Summarise one fault-seeding job: per suite type, how many tests ran, which failed and why.

usage: fault_results.py <bug> <results-dir> <out.json>
<results-dir>/<suite>/ holds that suite's surefire XML files plus exit-code and seconds files.
"""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET


def first_line(text):
    for line in (text or "").splitlines():
        line = line.strip()
        if line:
            return line[:300]
    return ""


def read(d, fname, default):
    p = os.path.join(d, fname)
    return open(p).read().strip() if os.path.exists(p) else default


def main():
    bug, root, out = sys.argv[1], sys.argv[2], sys.argv[3]
    suites = {}
    for d in sorted(p for p in glob.glob(os.path.join(root, "*")) if os.path.isdir(p)):
        name = os.path.basename(d)
        tests = failed = skipped = 0
        failures = []
        for f in sorted(glob.glob(os.path.join(d, "TEST-*.xml"))):
            for tc in ET.parse(f).getroot().iter("testcase"):
                tests += 1
                if tc.find("skipped") is not None:
                    skipped += 1
                    continue
                bad = tc.find("failure") if tc.find("failure") is not None else tc.find("error")
                if bad is not None:
                    failed += 1
                    failures.append({
                        "test": tc.get("classname", "").split(".")[-1] + " > " + tc.get("name", ""),
                        "type": (bad.get("type") or "").split(".")[-1],
                        "message": first_line(bad.get("message") or bad.text),
                    })
        rc = int(read(d, "exit-code", "-1"))
        suites[name] = {
            "tests": tests, "failed": failed, "skipped": skipped,
            "exit_code": rc, "seconds": float(read(d, "seconds", "0")),
            "detected": failed > 0 or rc != 0,
            "failures": failures,
            "note": read(d, "note", ""),
        }
    json.dump({"bug": bug, "suites": suites}, open(out, "w"), indent=2)
    summary = " | ".join(f"{k}={v['failed']}/{v['tests']}{'*' if v['detected'] else ''}" for k, v in suites.items())
    print(f"::notice title=fault-seeding {bug}::{summary}")


if __name__ == "__main__":
    main()
