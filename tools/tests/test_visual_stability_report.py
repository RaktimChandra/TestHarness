"""visual_stability_report.py: counts per browser and check, red runs and anti-aliasing."""
import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import visual_stability_report as vsr  # noqa: E402


def run(rep, differing, aa, failed=False, suite_failures=0, geometry=None):
    check = {"differing": differing, "compared": 69184, "ratio_pct": 100 * differing / 69184, "anti_aliased": aa}
    if geometry is not None:
        check["geometry"] = geometry
    return {"browser": "firefox", "rep": rep,
            "checks": {"cancel-dialog": check},
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

    def test_positions_group_runs_by_where_the_element_was_drawn_ignoring_scroll(self):
        at = "drawn at x=420 y=316.8 size 440x200.38 in viewport 1280x814"
        moved = "drawn at x=420 y=316.3 size 440x200.38 in viewport 1280x813"
        p = vsr.positions([run(1, 0, 0, geometry=at + " scrollY=0 dpr=1"),
                           run(2, 0, 0, geometry=at + " scrollY=950 dpr=1"),
                           run(3, 876, 777, failed=True, geometry=moved + " scrollY=12 dpr=1"),
                           run(4, 0, 0)])
        self.assertEqual(dict(p[("firefox", "cancel-dialog")]),
                         {at: [2, 0], moved: [1, 1], "not logged": [1, 0]})

    def test_environments_count_runs_with_a_failure(self):
        a = run(1, 0, 0)
        a["environment"] = {"image": "ubuntu24 20261012", "browser": "Mozilla Firefox 144.0"}
        b = run(2, 876, 777, failed=True)
        b["environment"] = {"image": "ubuntu24 20261019", "browser": "Mozilla Firefox 144.0"}
        c = run(3, 0, 0)
        e = vsr.environments([a, b, c])
        self.assertEqual(e[("firefox", "browser: Mozilla Firefox 144.0; image: ubuntu24 20261012")], [1, 0])
        self.assertEqual(e[("firefox", "browser: Mozilla Firefox 144.0; image: ubuntu24 20261019")], [1, 1])
        self.assertEqual(e[("firefox", "not recorded")], [1, 0])


if __name__ == "__main__":
    unittest.main()
