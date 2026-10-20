#!/usr/bin/env python3
"""Removes history files that a re-run attempt copied unchanged from an earlier attempt.

"Re-run failed jobs" keeps the artifacts of the jobs that passed, so the publish job of
attempt 2 stores their history files again under runs/<time>-<run id>-2/. Counted twice, the
same results double that run's durations and add same-commit "runs" to flakiness. A file in a
later attempt is dropped when the same relative path with identical content exists in an
earlier attempt of the same run; files the re-run produced (the re-run jobs) are kept.

usage: dedupe_attempts.py <ci-history>/runs     (idempotent; prints what it removed)
"""
import filecmp
import os
import re
import sys
from collections import defaultdict

DIR = re.compile(r"^(?P<time>\d{8}T\d{6}Z)-(?P<run>\d+)-(?P<attempt>\d+)$")


def dedupe(runs_dir):
    by_run = defaultdict(list)
    for name in os.listdir(runs_dir):
        m = DIR.match(name)
        if m and os.path.isdir(os.path.join(runs_dir, name)):
            by_run[m["run"]].append((int(m["attempt"]), name))
    removed = []
    for attempts in by_run.values():
        attempts.sort()
        for i, (_, later) in enumerate(attempts[1:], start=1):
            later_root = os.path.join(runs_dir, later)
            for dirpath, _, files in os.walk(later_root):
                for f in files:
                    path = os.path.join(dirpath, f)
                    rel = os.path.relpath(path, later_root)
                    for _, earlier in attempts[:i]:
                        other = os.path.join(runs_dir, earlier, rel)
                        if os.path.isfile(other) and filecmp.cmp(path, other, shallow=False):
                            os.remove(path)
                            removed.append(os.path.join(later, rel))
                            break
            for dirpath, dirs, files in sorted(os.walk(later_root), reverse=True):
                if not dirs and not files and not os.listdir(dirpath):
                    os.rmdir(dirpath)
    return removed


def main():
    removed = dedupe(sys.argv[1])
    for r in removed:
        print(f"duplicate of an earlier attempt, removed: {r}")
    print(f"{len(removed)} duplicate history file(s) removed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
