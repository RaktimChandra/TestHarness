#!/usr/bin/env python3
"""Test impact analysis experiment.

For every seeded bug, the "diff" is the source file that contains the bug's branch. The
experiment selects the tests the coverage map links to that file, runs only those with the bug
switched on, then runs the full API, UI and BDD suites with the same bug, and records time and
detection for both.

usage: tools/tia_experiment.py [--mvn "./mvnw -B"] [--skip-record] [--out experiments/tia]
Run from the repository root after `./mvnw install -DskipTests`.
"""
import argparse
import glob
import gzip
import json
import os
import shlex
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SUITES = ["suites/api-suite", "suites/ui-suite", "suites/bdd-suite"]
D = "sut/gate-domain/src/main/java/io/github/raktimchandra/gate/domain/"
A = "sut/gate-api/src/main/java/io/github/raktimchandra/gate/api/service/"
BUG_FILES = {
    "OFF_BY_ONE_CAPACITY": D + "CapacityPolicy.java",
    "CHECK_DIGIT_EDGE_CASE": D + "ContainerNumber.java",
    "TIMEZONE_SLOT_TIMES": D + "SlotTimes.java",
    "RACE_ON_LAST_SLOT": A + "BookingService.java",
    "MISSING_AUTHORISATION_CHECK": D + "BookingAccessPolicy.java",
    "API_SKIPS_CONTAINER_VALIDATION": A + "BookingService.java",
}


def sh(cmd, log):
    """Runs cmd with its output in log, then compresses the log (Maven logs are large)."""
    with open(log, "w") as f:
        start = time.time()
        rc = subprocess.call(cmd, stdout=f, stderr=subprocess.STDOUT)
        secs = round(time.time() - start, 1)
    with open(log, "rb") as src, gzip.open(log + ".gz", "wb", compresslevel=9) as dst:
        shutil.copyfileobj(src, dst)
    os.remove(log)
    return rc, secs


def results():
    tests = failed = 0
    test_s = 0.0
    failing = []
    for s in SUITES:
        for f in glob.glob(f"{s}/target/surefire-reports/TEST-*.xml"):
            for tc in ET.parse(f).getroot().iter("testcase"):
                if tc.find("skipped") is not None:
                    continue
                tests += 1
                test_s += float(tc.get("time") or 0)
                if tc.find("failure") is not None or tc.find("error") is not None:
                    failed += 1
                    failing.append(tc.get("classname", "").split(".")[-1] + " > " + tc.get("name", ""))
    return {"tests": tests, "failed": failed, "sum_test_seconds": round(test_s, 1), "failing": failing}


def clean_reports():
    for s in SUITES:
        subprocess.call(["rm", "-rf", f"{s}/target/surefire-reports"])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mvn", default="./mvnw -B")
    ap.add_argument("--cli", default="java -jar harness/harness-cli/target/harness-cli-1.0.0-SNAPSHOT.jar")
    ap.add_argument("--skip-record", action="store_true")
    ap.add_argument("--out", default="experiments/tia")
    ap.add_argument("--bugs", default=",".join(BUG_FILES))
    a = ap.parse_args()
    mvn, cli = shlex.split(a.mvn), shlex.split(a.cli)
    os.makedirs(a.out, exist_ok=True)
    logs = os.path.join(a.out, "logs")
    os.makedirs(logs, exist_ok=True)
    report = {"started": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "mvn": a.mvn, "runs": {}}

    if not a.skip_record:
        for s in SUITES:
            subprocess.call(["rm", "-rf", f"{s}/target/tia"])
        rc, secs = sh(mvn + ["verify", "-pl", ",".join(SUITES), "-Dharness.tia.record=true",
                             "-Djunit.jupiter.execution.parallel.enabled=false", "-Dmaven.test.failure.ignore=true"],
                      os.path.join(logs, "record.log"))
        report["record"] = {"exit": rc, "seconds": secs, **results()}
        print(f"recording: exit {rc} in {secs} s", flush=True)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    subprocess.check_call(cli + ["tia", "map", "--commit", commit, "--out", os.path.join(a.out, "test-impact-map.json")])
    tmap = json.load(open(os.path.join(a.out, "test-impact-map.json")))
    report["map"] = {"tests": len(tmap["tests"]),
                     "files": len({f for t in tmap["tests"].values() for f in t["files"]}),
                     "sum_test_ms": sum(t["durationMs"] for t in tmap["tests"].values())}

    for bug in a.bugs.split(","):
        changed = BUG_FILES[bug]
        sel = os.path.abspath(os.path.join(a.out, f"selection-{bug}.txt"))
        out = subprocess.check_output(cli + ["tia", "select", "--map", os.path.join(a.out, "test-impact-map.json"),
                                             "--files", changed, "--out", sel], text=True)
        selected = [l for l in open(sel).read().splitlines() if l.strip()]
        run = {"changed_file": changed, "selected": len(selected), "selector_output": out.strip().splitlines()}
        for mode, extra in (("selected", ["-Dharness.select=" + sel]), ("full", [])):
            clean_reports()
            rc, secs = sh(mvn + ["verify", "-pl", ",".join(SUITES), "-Dharness.sut.seeded-bugs=" + bug,
                                 "-Dmaven.test.failure.ignore=true", "-Dsurefire.failIfNoSpecifiedTests=false"] + extra,
                          os.path.join(logs, f"{bug}-{mode}.log"))
            r = results()
            run[mode] = {"exit": rc, "maven_seconds": secs, **r, "caught": r["failed"] > 0}
            print(f"{bug} {mode}: {r['tests']} tests, {r['failed']} failed, {secs} s", flush=True)
        report["runs"][bug] = run
        json.dump(report, open(os.path.join(a.out, "tia-experiment.json"), "w"), indent=2)
    report["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    json.dump(report, open(os.path.join(a.out, "tia-experiment.json"), "w"), indent=2)


if __name__ == "__main__":
    sys.exit(main())
