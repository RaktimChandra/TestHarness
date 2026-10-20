"""visual_stability_report.py: counts per browser and check, red runs and anti-aliasing."""
import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import visual_stability_report as vsr  # noqa: E402


def run(rep, differing, aa, failed=False, suite_failures=0):
    return {"browser": "firefox", "rep": rep,
            "checks": {"cancel-dialog": {"differing": differing, "compared": 69184,
                                         "ratio_pct": 100 * differing / 69184, "anti_aliased": aa}},
            "visual_failures": [{"check": "cancel-dialog", "pct": 0.552}] if failed else [],
            "retaken": [], "suite": {"tests": 29, "failures": suite_failures, "errors": 0, "skipped": 0}}


class Report(unittest.TestCase):
    def test_counts_failures_red_runs_and_anti_aliasing(self):
        rows, suites = vsr.summarise([run(1, 0, 0), run(2, 0, 212), run(3, 382, 40, failed=True, suite_failures=1)])
        row = rows[("firefox", "cancel-dialog")]
        self.assertEqual((row["runs"], row["failed"], row["runs_with_aa"]), (3, 1, 2))
        self.assertEqual((row["max_differing"], row["max_aa"]), (382, 212))
        self.assertEqual(suites["firefox"], {"runs": 3, "red": 1, "retaken": 0})

    def test_a_run_without_a_suite_summary_counts_as_red(self):
        r = run(1, 0, 0)
        r["suite"] = None
        _, suites = vsr.summarise([r])
        self.assertEqual(suites["firefox"]["red"], 1)


if __name__ == "__main__":
    unittest.main()
