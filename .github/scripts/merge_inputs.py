"""Merges the per-job dashboard inputs of one pipeline.

usage: merge_inputs.py <unpacked dir> <repo root> <history run dir>

<unpacked dir>/<job>/ holds each job's files with repository-relative paths. Test history goes
to <history run dir>/<job>/ (jobs can produce identically named files, e.g. two browsers
starting in the same second), never into the repository. Healing logs are concatenated. Other
files (coverage, mutations, axe reports, visual diffs) are copied into the repository without
overwriting: coverage comes from the build job only, as suite jobs do not send it.
"""
import os
import shutil
import sys

unpacked, root, run_dir = sys.argv[1:4]
counts = {"history": 0, "healing": 0, "other": 0, "skipped": 0}
for job in sorted(os.listdir(unpacked)):
    base = os.path.join(unpacked, job)
    for dirpath, _, files in os.walk(base):
        for name in files:
            src = os.path.join(dirpath, name)
            rel = os.path.relpath(src, base)
            parts = rel.split(os.sep)
            if "test-history" in parts and name.endswith(".jsonl"):
                dst = os.path.join(run_dir, job, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                counts["history"] += 1
            elif name == "healing.jsonl":
                dst = os.path.join(root, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(src, "rb") as i, open(dst, "ab") as o:
                    o.write(i.read())
                counts["healing"] += 1
            else:
                dst = os.path.join(root, rel)
                if os.path.exists(dst):
                    counts["skipped"] += 1
                    continue
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                counts["other"] += 1
print("merged:", ", ".join(f"{k} {v}" for k, v in counts.items()))
