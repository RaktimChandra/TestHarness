#!/usr/bin/env python3
"""Evaluates the AI Gherkin drafter against a real model served over an OpenAI-compatible API.

For every user story in --stories (one per .md file) and every repeat, `th ai gherkin` drafts a
feature with the model; the draft is moved to <out>/<model>/drafts/. Then the bdd-suite's
DraftStepCoverage matches every drafted step against the suite's real step definitions. Per
model the result is: how many drafts are valid Gherkin, scenarios per draft, the share of
drafted steps an existing step definition already runs ("step reuse"), and draft latency.

Optionally (--explain) the api-suite is run with a seeded bug and `th ai explain` asks the
model to summarise the failures; that output is kept as a qualitative sample only.

usage: tools/ai_eval.py --model qwen2.5:1.5b [--base-url http://localhost:11434/v1]
       [--stories experiments/ai/stories] [--out experiments/ai/results] [--repeats 1]
       [--cli ./th] [--mvn "./mvnw -B -ntp"] [--explain]
Run from the repository root after `./mvnw install -DskipTests`.
"""
import argparse
import glob
import json
import os
import re
import shlex
import shutil
import statistics
import subprocess
import sys
import time


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--stories", default="experiments/ai/stories")
    ap.add_argument("--out", default="experiments/ai/results")
    ap.add_argument("--repeats", type=int, default=1)
    ap.add_argument("--timeout", default="15m")
    ap.add_argument("--cli", default="./th")
    ap.add_argument("--mvn", default="./mvnw -B -ntp")
    ap.add_argument("--explain", action="store_true")
    a = ap.parse_args()

    out = os.path.abspath(os.path.join(a.out, slug(a.model)))
    drafts_dir = os.path.join(out, "drafts")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(drafts_dir)
    props = [f"-Dharness.ai.provider=openai", f"-Dharness.ai.model={a.model}",
             f"-Dharness.ai.base-url={a.base_url}", f"-Dharness.ai.timeout={a.timeout}"]
    cli = shlex.split(a.cli)
    report = {"model": a.model, "base_url": a.base_url, "protocol": "openai-compatible", "temperature": 0.2,
              "started": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "drafts": []}
    for story in sorted(glob.glob(os.path.join(a.stories, "*.md"))):
        name = os.path.splitext(os.path.basename(story))[0]
        for r in range(1, a.repeats + 1):
            t0 = time.time()
            p = subprocess.run(cli + props + ["ai", "gherkin", "--story-file", story], capture_output=True, text=True)
            seconds = round(time.time() - t0, 1)
            entry = {"story": name, "repeat": r, "seconds": seconds, "exit": p.returncode}
            m = re.search(r"DRAFT written to (.+?\.feature) \((.*)\)", p.stdout)
            if m and os.path.exists(m.group(1)):
                target = os.path.join(drafts_dir, f"{name}-r{r}.feature")
                shutil.move(m.group(1), target)
                entry["file"] = os.path.basename(target)
                entry["valid_gherkin"] = "valid Gherkin" in m.group(2) and "NOT" not in m.group(2)
                sc = re.match(r"(\d+) scenarios", m.group(2))
                entry["scenarios"] = int(sc.group(1)) if sc else 0
            else:
                entry["error"] = (p.stdout + p.stderr).strip()[-600:]
            report["drafts"].append(entry)
            print(f"{a.model} {name} r{r}: {entry.get('scenarios', '-')} scenarios, "
                  f"valid={entry.get('valid_gherkin')}, {seconds} s", flush=True)

    cov_file = os.path.join(out, "coverage.json")
    c = subprocess.run(shlex.split(a.mvn) + ["test", "-pl", "suites/bdd-suite", "-Dtest=DraftStepCoverageTest",
                                              f"-Dai.drafts={drafts_dir}", f"-Dai.coverage.out={cov_file}"],
                       capture_output=True, text=True)
    if c.returncode != 0 or not os.path.exists(cov_file):
        print(c.stdout[-3000:])
        raise SystemExit("step coverage failed")
    cov = json.load(open(cov_file))
    by_file = {f["file"]: f for f in cov["files"]}
    for d in report["drafts"]:
        f = by_file.get(d.get("file"))
        if f:
            d.update({"steps": f["steps"], "matched_steps": f["matched"], "unmatched": f["unmatched"],
                      "ambiguous": f["ambiguous"]})

    drafts = report["drafts"]
    made = [d for d in drafts if "file" in d]
    valid = [d for d in made if d.get("valid_gherkin")]
    steps = sum(d.get("steps", 0) for d in valid)
    matched = sum(d.get("matched_steps", 0) for d in valid)
    report["summary"] = {
        "stories": len({d["story"] for d in drafts}), "attempts": len(drafts), "drafts_written": len(made),
        "valid_gherkin": len(valid),
        "valid_gherkin_pct": round(100.0 * len(valid) / len(drafts), 1) if drafts else None,
        "scenarios_per_valid_draft": round(statistics.mean([d["scenarios"] for d in valid]), 2) if valid else None,
        "steps": steps, "matched_steps": matched,
        "step_reuse_pct": round(100.0 * matched / steps, 1) if steps else None,
        "drafts_fully_runnable": sum(1 for d in valid if d.get("steps") and d.get("matched_steps") == d.get("steps")),
        "median_seconds": statistics.median([d["seconds"] for d in drafts]) if drafts else None,
        "step_definitions": cov["stepDefinitions"],
    }

    if a.explain:
        log = os.path.join(out, "seeded-run.log")
        subprocess.run(shlex.split(a.mvn) + ["verify", "-pl", "suites/api-suite", "-Dmaven.test.failure.ignore=true",
                                             "-Dharness.sut.seeded-bugs=OFF_BY_ONE_CAPACITY"],
                       stdout=open(log, "w"), stderr=subprocess.STDOUT)
        e = subprocess.run(cli + props + ["ai", "explain"], capture_output=True, text=True)
        report["explain"] = {"seeded_bug": "OFF_BY_ONE_CAPACITY", "output": (e.stdout + e.stderr).strip()[-1500:]}
        for f in glob.glob("target/ai-drafts/*.md"):
            shutil.copy(f, os.path.join(out, "explain-" + os.path.basename(f)))
        subprocess.run(["gzip", "-9", log])

    report["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    json.dump(report, open(os.path.join(out, "ai-eval.json"), "w"), indent=2)
    print(json.dumps(report["summary"], indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
