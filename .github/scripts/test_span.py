#!/usr/bin/env python3
"""Seconds between surefire's "T E S T S" banner (test JVM starting) and its "Results:" line.

Needs Maven run with -Dorg.slf4j.simpleLogger.showDateTime=true
-Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS, so the span measures test execution
only (no Maven start-up, dependency resolution or report writing). "Running <class>" lines
are not usable as a start marker: with parallel execution surefire prints them when a class
finishes.

usage: test_span.py <maven log>   -> prints the span in seconds with one decimal
"""
import re
import sys
from datetime import datetime, timedelta

LINE = re.compile(r"^(\d{2}:\d{2}:\d{2}\.\d{3}) \[INFO\] (.*)$")


def main() -> int:
    first = last = None
    for raw in open(sys.argv[1], encoding="utf-8", errors="replace"):
        m = LINE.match(raw.rstrip("\n"))
        if not m:
            continue
        t = datetime.strptime(m.group(1), "%H:%M:%S.%f")
        if first is None and m.group(2).strip() == "T E S T S":
            first = t
        if m.group(2).strip() == "Results:":
            last = t
    if first is None or last is None:
        print("n/a")
        return 0
    span = last - first
    if span < timedelta(0):  # crossed midnight
        span += timedelta(days=1)
    print(f"{span.total_seconds():.1f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
