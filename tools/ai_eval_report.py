#!/usr/bin/env python3
"""Aggregates <dir>/*/ai-eval.json (tools/ai_eval.py) into <dir>/ai-eval.md.

usage: tools/ai_eval_report.py <dir>
"""
import glob
import json
import os
import sys


def main():
    d = sys.argv[1]
    reports = [json.load(open(f)) for f in sorted(glob.glob(os.path.join(d, "*", "ai-eval.json")))]
    if not reports:
        print("no ai-eval.json files")
        return 1
    lines = ["# AI Gherkin drafter: evaluation", "",
             f"Stories: {reports[0]['summary']['stories']}; step definitions in the BDD suite: "
             f"{reports[0]['summary']['step_definitions']}; temperature {reports[0]['temperature']}. "
             "Step reuse = drafted steps (in valid drafts) that an existing step definition matches.", "",
             "| model | attempts | valid Gherkin | scenarios / draft | step reuse | fully runnable drafts | median s / draft |",
             "|---|---:|---:|---:|---:|---:|---:|"]
    for r in reports:
        s = r["summary"]
        lines.append(f"| {r['model']} | {s['attempts']} | {s['valid_gherkin']} ({s['valid_gherkin_pct']} %) | "
                     f"{s['scenarios_per_valid_draft']} | {s['matched_steps']} / {s['steps']} ({s['step_reuse_pct']} %) | "
                     f"{s['drafts_fully_runnable']} | {s['median_seconds']} |")
    lines += ["", "## Most common steps no definition matches", ""]
    for r in reports:
        counts = {}
        for dr in r["drafts"]:
            for u in dr.get("unmatched", []):
                counts[u] = counts.get(u, 0) + 1
        top = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))[:8]
        lines.append(f"**{r['model']}**: " + ("; ".join(f"\"{k}\" ({v})" for k, v in top) or "none"))
        lines.append("")
    text = "\n".join(lines) + "\n"
    open(os.path.join(d, "ai-eval.md"), "w").write(text)
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
