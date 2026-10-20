"""Both Gatling console parsers (CI summary and capacity experiment) against real output shapes.

Run: python3 -m unittest discover -s tools/tests
"""
import os
import subprocess
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import capacity  # noqa: E402

# Shape of Gatling 3.16's final console summary (from the capacity run 39410838330, trimmed).
OUTPUT = """
================================================================================
---- Requests -----------------------------------------------------------------------|---Total---|-----OK----|----KO----
> Global                                                                             |     4,800 |     4,790 |        10
> list slots                                                                         |     1,200 |     1,200 |         0
> book                                                                               |     1,200 |     1,195 |         5
> read booking                                                                       |     1,200 |     1,200 |         0
> cancel                                                                             |     1,200 |     1,195 |         5

---- Booking flow, already signed in -------------------------------------------------
================================================================================
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      |     4,800 |     4,790 |        10
> max response time (ms)                                                             |        21 |        21 |         -
> response time 50th percentile (ms)                                                 |         2 |         2 |         -
> response time 95th percentile (ms)                                                 |         4 |         4 |         -
> response time 99th percentile (ms)                                                 |         6 |         6 |         -
> mean throughput (rps)                                                              |        80 |        80 |         -
---- Response Time Distribution -------------------------------------------------------
"""


class CapacityParser(unittest.TestCase):
    def test_counts_with_thousands_separators(self):
        r = capacity.parse_gatling(OUTPUT)
        self.assertEqual(r["requests"], 4800)
        self.assertEqual(r["failed"], 10)
        self.assertAlmostEqual(r["error_pct"], 0.208)
        self.assertEqual(r["p95_ms"], 4.0)
        self.assertEqual(r["by_request"]["cancel"], {"total": 1200, "ok": 1195, "ko": 5})

    def test_missing_summary_is_an_error_not_zero(self):
        with self.assertRaises(RuntimeError):
            capacity.parse_gatling("no summary here")


class CiSummary(unittest.TestCase):
    def test_notice_carries_full_counts(self):
        with tempfile.NamedTemporaryFile("w", suffix=".log", delete=False) as f:
            f.write(OUTPUT)
        env = {k: v for k, v in os.environ.items() if k != "GITHUB_STEP_SUMMARY"}
        out = subprocess.run([sys.executable, os.path.join(ROOT, ".github", "scripts", "gatling_summary.py"),
                              "perf", f.name], capture_output=True, text=True, env=env, check=True).stdout
        os.unlink(f.name)
        notice = [line for line in out.splitlines() if line.startswith("::notice")][0]
        self.assertIn("requests=4800", notice)
        self.assertIn("failed_(KO)=10", notice)
        self.assertIn("cancel=1200/5", notice)


if __name__ == "__main__":
    unittest.main()
