#!/usr/bin/env python3
"""Exports the latest CIForge run from its data directory: the run, builds and steps as stored
in CIForge's SQLite database, the key lines of each build log, and a Markdown summary.

usage: tools/ciforge_export.py <ciforge data dir> <out dir>
"""
import json
import os
import re
import sqlite3
import sys

KEY = re.compile(r"Tests run: \d+, Failures|BUILD (SUCCESS|FAILURE)|suites: \d+ tests|mutations|p95|p99|"
                 r"Generated \d+ mutations|Killed|ERROR\]")


def main():
    data, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    db = sqlite3.connect(os.path.join(data, "ciforge.db"))
    db.row_factory = sqlite3.Row
    run = db.execute("select * from runs order by created_at desc limit 1").fetchone()
    if run is None:
        print("no CIForge run recorded")
        return 1
    rid = run["id"]
    builds = [dict(r) for r in db.execute("select * from builds where run_id=? order by started_at, position", (rid,))]
    steps = [dict(r) for r in db.execute("select * from steps where run_id=? order by build_id, idx", (rid,))]
    extracts = {}
    for s in steps:
        p = s.get("log_path")
        if p and os.path.exists(p):
            lines = [l.rstrip() for l in open(p, encoding="utf-8", errors="replace") if KEY.search(l)]
            extracts[f"{s['build_id']}#{s['idx']}"] = lines[-15:]
        s["log_path"] = os.path.relpath(p, data) if p else None
    doc = {"run": dict(run), "builds": builds, "steps": steps, "log_extracts": extracts}
    json.dump(doc, open(os.path.join(out, f"run-{rid}.json"), "w"), indent=2)
    md = [f"### CIForge run {rid}: {run['status']}", "",
          f"Commit `{(run['commit_sha'] or '')[:7]}` on `{run['branch']}`, {run['started_at']} → {run['finished_at']}", "",
          "| build | status | started | finished | reason |", "|---|---|---|---|---|"]
    md += [f"| {b['build_id']} | {b['status']} | {b['started_at']} | {b['finished_at']} | {b['reason'] or ''} |" for b in builds]
    open(os.path.join(out, f"run-{rid}.md"), "w").write("\n".join(md) + "\n")
    print("\n".join(md))
    return 0 if run["status"] == "success" else 1


if __name__ == "__main__":
    sys.exit(main())
