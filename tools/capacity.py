#!/usr/bin/env python3
"""Capacity experiment: how many booking flows per second the Gate SUT sustains.

The SUT runs in Docker Compose with fixed CPU and memory limits (docker-compose.capacity.yml),
so the answer is "capacity on N CPUs", not "capacity of whatever runner this was". For each
flow kind (full: sign-in on every flow; authenticated: token reused) the arrival rate is
stepped up; each level is one Gatling run of CapacitySimulation at a constant open arrival
rate. While a level runs, `docker stats` samples the SUT's CPU.

A level KEEPS UP when errors < 1 %, p95 <= 800 ms (the smoke test's gate) and completed flows
per second >= 95 % of the offered rate. Capacity = the highest level that keeps up. Stepping
stops after two consecutive levels that do not.

usage: tools/capacity.py --flow full --rates 2,4,8,12,16,24 [--duration 60] [--mvn "./mvnw -B -ntp"]
       [--out experiments/capacity] [--label run-1]
Run from the repository root with the SUT already started:
  docker compose -f docker-compose.yml -f docker-compose.capacity.yml up -d --build --wait
"""
import argparse
import json
import os
import re
import shlex
import statistics
import subprocess
import sys
import threading
import time

SLO_P95_MS = 800
MAX_ERROR_PCT = 1.0
MIN_ACHIEVED = 0.95


def parse_gatling(text):
    """Global figures and per-request counts from Gatling's console summary."""
    blocks = text.split("---- Global Information")
    if len(blocks) < 2:
        raise RuntimeError("no Gatling global summary in the output")
    block = blocks[-1].split("========")[0]
    g = {}
    for line in block.splitlines():
        # Gatling groups thousands with commas ("1,500"); the first version of this parser did
        # not, so every level with 1,000+ requests read as 0 requests and "did not keep up".
        m = re.match(r"> (.+?)\s*\|\s*([\d.,]+)\s*\|\s*([\d.,]+|-)\s*\|\s*([\d.,]+|-)", line)
        if m:
            g[m.group(1).strip()] = [x.replace(",", "") for x in (m.group(2), m.group(3), m.group(4))]
    reqs = {}
    tables = text.split("---- Requests ----")
    if len(tables) > 1:
        for line in tables[-1].split("\n\n")[0].splitlines():
            m = re.match(r"> (.+?)\s*\|\s*([\d,]+)\s*\|\s*([\d,]+)\s*\|\s*([\d,]+)", line)
            if m and m.group(1).strip() != "Global":
                n = [int(x.replace(",", "")) for x in (m.group(2), m.group(3), m.group(4))]
                reqs[m.group(1).strip()] = {"total": n[0], "ok": n[1], "ko": n[2]}
    num = lambda k, i=0: float(g[k][i]) if k in g and g[k][i] not in ("-",) else None
    total = int(num("request count") or 0)
    ko = int(num("request count", 2) or 0)
    return {
        "requests": total, "failed": ko,
        "error_pct": round(100.0 * ko / total, 3) if total else None,
        "p50_ms": num("response time 50th percentile (ms)"),
        "p95_ms": num("response time 95th percentile (ms)"),
        "p99_ms": num("response time 99th percentile (ms)"),
        "max_ms": num("max response time (ms)"),
        "rps": num("mean throughput (rps)"),
        "by_request": reqs,
    }


class CpuSampler(threading.Thread):
    """Samples `docker stats` CPU % of one container every few seconds."""

    def __init__(self, container):
        super().__init__(daemon=True)
        self.container, self.samples, self.stop = container, [], threading.Event()

    def run(self):
        while not self.stop.is_set():
            try:
                out = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}", self.container],
                                     capture_output=True, text=True, timeout=20).stdout.strip()
                if out.endswith("%"):
                    self.samples.append(float(out[:-1]))
            except (subprocess.SubprocessError, ValueError, OSError):
                pass
            self.stop.wait(5)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--flow", choices=["full", "authenticated"], required=True)
    ap.add_argument("--rates", required=True, help="comma-separated flows/s, ascending")
    ap.add_argument("--duration", type=int, default=60)
    ap.add_argument("--mvn", default="./mvnw -B -ntp")
    ap.add_argument("--container", default="testharness-gate-1")
    ap.add_argument("--out", default="experiments/capacity")
    ap.add_argument("--label", default=time.strftime("%Y%m%d-%H%M%S"))
    a = ap.parse_args()
    os.makedirs(os.path.join(a.out, "logs"), exist_ok=True)
    limits = subprocess.run(["docker", "inspect", "--format", "{{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}",
                             a.container], capture_output=True, text=True).stdout.split()
    report = {"flow": a.flow, "label": a.label, "duration_s": a.duration, "started": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
              "sut_cpus": int(limits[0]) / 1e9 if limits and limits[0] != "0" else None,
              "sut_memory_bytes": int(limits[1]) if len(limits) > 1 and limits[1] != "0" else None,
              "host_cpus": os.cpu_count(), "rules": {"p95_ms": SLO_P95_MS, "max_error_pct": MAX_ERROR_PCT,
                                                     "min_achieved_ratio": MIN_ACHIEVED},
              "levels": []}
    misses = 0
    for rate in [float(r) for r in a.rates.split(",")]:
        log = os.path.join(a.out, "logs", f"{a.label}-{a.flow}-{rate:g}.log")
        cmd = shlex.split(a.mvn) + ["verify", "-pl", "suites/perf-suite",
                                    "-Dperf.simulation=io.github.raktimchandra.testharness.perf.CapacitySimulation",
                                    f"-Dperf.rate={rate:g}", f"-Dperf.duration={a.duration}", f"-Dperf.flow={a.flow}",
                                    "-Dharness.env=docker"]
        sampler = CpuSampler(a.container)
        sampler.start()
        t0 = time.time()
        proc = subprocess.run(cmd, capture_output=True, text=True)
        sampler.stop.set()
        open(log, "w").write(proc.stdout + proc.stderr)
        level = {"offered_flows_per_s": rate, "maven_exit": proc.returncode, "wall_s": round(time.time() - t0, 1)}
        try:
            level.update(parse_gatling(proc.stdout))
        except RuntimeError as e:
            level["error"] = str(e)
        cancel_ok = level.get("by_request", {}).get("cancel", {}).get("ok", 0)
        level["completed_flows_per_s"] = round(cancel_ok / a.duration, 2)
        level["achieved_ratio"] = round(level["completed_flows_per_s"] / rate, 3) if rate else None
        cpu = sampler.samples
        level["sut_cpu_pct_mean"] = round(statistics.mean(cpu), 1) if cpu else None
        level["sut_cpu_pct_max"] = round(max(cpu), 1) if cpu else None
        keeps_up = ("error" not in level and level.get("error_pct") is not None
                    and level["error_pct"] < MAX_ERROR_PCT and (level.get("p95_ms") or 1e9) <= SLO_P95_MS
                    and (level["achieved_ratio"] or 0) >= MIN_ACHIEVED)
        level["keeps_up"] = keeps_up
        report["levels"].append(level)
        print(f"{a.flow} @ {rate:g}/s: p95 {level.get('p95_ms')} ms, errors {level.get('error_pct')} %, "
              f"completed {level['completed_flows_per_s']}/s, SUT CPU {level['sut_cpu_pct_mean']} % "
              f"-> {'keeps up' if keeps_up else 'DOES NOT keep up'}", flush=True)
        misses = 0 if keeps_up else misses + 1
        if misses >= 2:
            break
    ok = [l["offered_flows_per_s"] for l in report["levels"] if l["keeps_up"]]
    report["capacity_flows_per_s"] = max(ok) if ok else 0
    report["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    path = os.path.join(a.out, f"capacity-{a.label}-{a.flow}.json")
    json.dump(report, open(path, "w"), indent=2)
    print(f"capacity ({a.flow}): {report['capacity_flows_per_s']} flows/s -> {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
