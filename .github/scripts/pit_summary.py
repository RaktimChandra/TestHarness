#!/usr/bin/env python3
"""Writes the PIT mutation score and every surviving mutant to the job summary.

usage: pit_summary.py <mutations.xml>
"""
import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter


def main(path: str) -> int:
    if not os.path.exists(path):
        print(f"no PIT report at {path}")
        return 0
    ms = list(ET.parse(path).getroot().iter("mutation"))
    status = Counter(m.get("status") for m in ms)
    detected = status["KILLED"] + status["TIMED_OUT"] + status["MEMORY_ERROR"]
    score = 100.0 * detected / len(ms) if ms else 0.0
    lines = [f"### Mutation testing (PIT) — {score:.1f} % ({detected}/{len(ms)} mutants detected)", "",
             "| Status | Mutants |", "|---|---:|"]
    lines += [f"| {s} | {n} |" for s, n in sorted(status.items())]
    alive = [m for m in ms if m.get("status") in ("SURVIVED", "NO_COVERAGE")]
    if alive:
        lines += ["", "| Survived | Where | Mutation |", "|---|---|---|"]
        for m in alive:
            where = f"{m.findtext('mutatedClass', '').split('.')[-1]}.{m.findtext('mutatedMethod', '')}:{m.findtext('lineNumber', '')}"
            lines.append(f"| {m.get('status')} | `{where}` | {m.findtext('description', '')} |")
    text = "\n".join(lines) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "sut/gate-domain/target/pit-reports/mutations.xml"))
