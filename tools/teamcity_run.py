#!/usr/bin/env python3
"""Runs this repository's TeamCity Kotlin DSL on a real TeamCity server and records the result.

Expects a TeamCity server on --url (fresh data directory) and one agent connecting to it
(teamcity.yml starts both). Steps, all through the REST API with the super-user token:
  1. create project "TestHarness" with a Git VCS root for --repo / --branch
  2. enable versioned settings in Kotlin, "use settings from VCS", importing .teamcity/settings.kts
     from that branch - TeamCity compiles the DSL itself
  3. authorise the agent, queue the requested builds (their snapshot dependencies follow)
  4. wait for every queued build and its dependencies; write <out>/teamcity-run.json and .md

usage: tools/teamcity_run.py --token-file F --repo URL --branch B --builds ApiSuite,BddSuite
       [--url http://localhost:8111] [--out experiments/teamcity] [--timeout-min 150]
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request


class TC:
    def __init__(self, url, token):
        self.url = url.rstrip("/")
        self.auth = "Basic " + base64.b64encode(f":{token}".encode()).decode()

    def call(self, method, path, body=None, accept="application/json", ctype="application/json"):
        data = None
        if body is not None:
            data = (json.dumps(body) if ctype == "application/json" else body).encode()
        req = urllib.request.Request(self.url + path, data=data, method=method,
                                     headers={"Authorization": self.auth, "Accept": accept, "Content-Type": ctype,
                                              "Origin": self.url})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                raw = r.read().decode()
                return json.loads(raw) if raw and accept == "application/json" else raw
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"{method} {path} -> {e.code}: {e.read().decode()[:800]}") from None

    def get(self, path):
        return self.call("GET", path)


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


def wait(what, fn, timeout, every=5):
    end = time.time() + timeout
    last = None
    while time.time() < end:
        try:
            v = fn()
            if v:
                return v
        except Exception as e:  # noqa: BLE001 - keep polling, report the last error
            last = e
        time.sleep(every)
    raise SystemExit(f"timed out waiting for {what}" + (f" (last error: {last})" if last else ""))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8111")
    ap.add_argument("--token-file", required=True)
    ap.add_argument("--repo", required=True)
    ap.add_argument("--branch", required=True)
    ap.add_argument("--builds", required=True)
    ap.add_argument("--out", default="experiments/teamcity")
    ap.add_argument("--timeout-min", type=int, default=150)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    tc = TC(a.url, open(a.token_file).read().strip())
    report = {"server": a.url, "repo": a.repo, "branch": a.branch, "started": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}

    server = wait("the REST API", lambda: tc.get("/app/rest/server"), 600)
    report["teamcity_version"] = server.get("version")
    log(f"TeamCity {server.get('version')}")

    tc.call("POST", "/app/rest/projects", {"id": "TestHarness", "name": "TestHarness",
                                           "parentProject": {"locator": "id:_Root"}})
    tc.call("POST", "/app/rest/vcs-roots", {
        "id": "TestHarness_Repo", "name": "TestHarness repository", "vcsName": "jetbrains.git",
        "project": {"id": "TestHarness"},
        "properties": {"property": [
            {"name": "url", "value": a.repo}, {"name": "branch", "value": f"refs/heads/{a.branch}"},
            {"name": "teamcity:branchSpec", "value": "+:refs/heads/*"}, {"name": "authMethod", "value": "ANONYMOUS"}]}})
    log("project and VCS root created; enabling versioned settings (Kotlin, from VCS)")
    tc.call("PUT", "/app/rest/projects/id:TestHarness/versionedSettings/config", {
        "synchronizationMode": "enabled", "vcsRootId": "TestHarness_Repo", "format": "kotlin",
        "buildSettingsMode": "useFromVCS", "allowUIEditing": False, "storeSecureValuesOutsideVcs": True,
        "portableDsl": True, "showSettingsChanges": True, "importDecision": "importFromVCS"})

    def imported():
        bts = tc.get("/app/rest/buildTypes?locator=affectedProject:(id:TestHarness)&fields=buildType(id,name)")
        status = tc.call("GET", "/app/rest/projects/id:TestHarness/versionedSettings/status")
        log(f"settings status: {json.dumps(status)[:300]}; build types: {len(bts.get('buildType', []))}")
        return bts.get("buildType") or None
    build_types = wait("the DSL to be imported and compiled by TeamCity", imported, 900, every=15)
    report["build_types"] = [b["id"] for b in build_types]
    log("build types: " + ", ".join(report["build_types"]))

    agent = wait("an agent to register", lambda: (tc.get("/app/rest/agents?locator=authorized:any&fields=agent(id,name,authorized,connected)")
                                                  .get("agent") or [None])[0], 900, every=10)
    if not agent.get("authorized"):
        tc.call("PUT", f"/app/rest/agents/id:{agent['id']}/authorized", "true", accept="text/plain", ctype="text/plain")
    log(f"agent {agent['name']} authorised")
    report["agent"] = agent["name"]

    def resolve(short):
        for b in report["build_types"]:
            if b == short or b.endswith("_" + short):
                return b
        raise SystemExit(f"no build type {short} in {report['build_types']}")
    queued = []
    for short in a.builds.split(","):
        bt = resolve(short.strip())
        q = tc.call("POST", "/app/rest/buildQueue", {"buildType": {"id": bt}, "branchName": a.branch,
                                                      "comment": {"text": "teamcity.yml"}})
        queued.append(q["id"])
        log(f"queued {bt} as build {q['id']}")

    deadline = time.time() + a.timeout_min * 60
    builds = {}
    while time.time() < deadline:
        found = tc.get("/app/rest/builds?locator=affectedProject:(id:TestHarness),defaultFilter:false,state:any,count:100"
                       "&fields=build(id,buildTypeId,state,status,statusText,startDate,finishDate,queuedDate,"
                       "testOccurrences(count,passed,failed,ignored),problemOccurrences(count))")
        builds = {b["id"]: b for b in found.get("build", [])}
        open_ = [b for b in builds.values() if b["state"] != "finished"]
        log(f"{len(builds)} builds, {len(open_)} not finished: " + ", ".join(f"{b['buildTypeId']}={b['state']}" for b in open_))
        if builds and not open_ and all(q in builds for q in queued):
            break
        if any(b["state"] == "queued" for b in open_):
            for b in open_:
                if b["state"] == "queued":
                    why = tc.get(f"/app/rest/buildQueue/id:{b['id']}?fields=waitReason")
                    log(f"  {b['buildTypeId']} waiting: {why.get('waitReason')}")
        time.sleep(30)
    else:
        report["timed_out"] = True

    rows = []
    for b in sorted(builds.values(), key=lambda b: b.get("startDate") or b.get("queuedDate") or ""):
        t = b.get("testOccurrences") or {}
        rows.append({"id": b["id"], "build_type": b["buildTypeId"], "state": b["state"], "status": b.get("status"),
                     "status_text": b.get("statusText"), "started": b.get("startDate"), "finished": b.get("finishDate"),
                     "tests": t.get("count", 0), "passed": t.get("passed", 0), "failed": t.get("failed", 0),
                     "ignored": t.get("ignored", 0)})
    report["builds"] = rows
    report["finished"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    report["success"] = bool(rows) and all(r["status"] == "SUCCESS" for r in rows) and not report.get("timed_out")
    json.dump(report, open(os.path.join(a.out, "teamcity-run.json"), "w"), indent=2)
    md = [f"# TeamCity run ({report['teamcity_version']})", "",
          f"Branch `{a.branch}`, agent `{report['agent']}`, settings imported from `.teamcity/settings.kts` by the server.", "",
          "| build | status | tests | failed | ignored | started | finished | status text |", "|---|---|---:|---:|---:|---|---|---|"]
    md += [f"| {r['build_type']} | {r['status']} | {r['tests']} | {r['failed']} | {r['ignored']} | {r['started']} | "
           f"{r['finished']} | {(r['status_text'] or '').replace('|', '/')} |" for r in rows]
    open(os.path.join(a.out, "teamcity-run.md"), "w").write("\n".join(md) + "\n")
    print("\n".join(md))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    sys.exit(main())
