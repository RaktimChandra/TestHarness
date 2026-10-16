#!/usr/bin/env python3
"""The single required check: passes only if every blocking job succeeded.

Reads the `needs` context (as JSON in $NEEDS). Jobs listed in NON_BLOCKING are reported but
never fail the gate — the quarantine job runs known-flaky tests on purpose. A blocking job
that was skipped or cancelled fails the gate too: "did not run" is not "passed".
"""
import json
import os
import sys

NON_BLOCKING = {"quarantined"}


def main() -> int:
    needs = json.loads(os.environ["NEEDS"])
    rows, failed = [], []
    for job, info in sorted(needs.items()):
        result = info.get("result", "unknown")
        blocking = job not in NON_BLOCKING
        if blocking and result != "success":
            failed.append(job)
        mark = "✅" if result == "success" else ("⚠️" if not blocking else "❌")
        rows.append(f"| {job} | {'blocking' if blocking else 'non-blocking (quarantine)'} | {mark} {result} |")
    verdict = "PASSED" if not failed else "FAILED: " + ", ".join(failed)
    text = "\n".join(["### Quality gate — " + verdict, "", "| Job | Kind | Result |", "|---|---|---|", *rows]) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
