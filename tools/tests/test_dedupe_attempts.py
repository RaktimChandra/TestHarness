"""dedupe_attempts.py: a re-run attempt keeps only the history it produced itself."""
import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, ".github", "scripts"))
import dedupe_attempts  # noqa: E402


def write(root, rel, text):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


class DedupeAttempts(unittest.TestCase):
    def test_copied_files_go_rerun_files_stay(self):
        with tempfile.TemporaryDirectory() as runs:
            a1, a2 = "20261020T110607Z-39410820490-1", "20261020T120905Z-39410820490-2"
            write(runs, f"{a1}/ui-chrome-1of2/h.jsonl", "chrome results\n")
            write(runs, f"{a1}/ui-firefox-1of2/h1.jsonl", "firefox, failed\n")
            write(runs, f"{a2}/ui-chrome-1of2/h.jsonl", "chrome results\n")        # copied
            write(runs, f"{a2}/ui-firefox-1of2/h2.jsonl", "firefox, re-run\n")     # produced
            write(runs, "20261021T090000Z-39500000000-1/build/h.jsonl", "chrome results\n")  # other run
            removed = dedupe_attempts.dedupe(runs)
            self.assertEqual(removed, [os.path.join(a2, "ui-chrome-1of2", "h.jsonl")])
            self.assertFalse(os.path.exists(os.path.join(runs, a2, "ui-chrome-1of2")))
            self.assertTrue(os.path.exists(os.path.join(runs, a2, "ui-firefox-1of2", "h2.jsonl")))
            self.assertTrue(os.path.exists(os.path.join(runs, "20261021T090000Z-39500000000-1", "build", "h.jsonl")))
            self.assertEqual(dedupe_attempts.dedupe(runs), [])  # idempotent

    def test_same_path_different_content_is_kept(self):
        with tempfile.TemporaryDirectory() as runs:
            write(runs, "20261020T110607Z-1-1/j/h.jsonl", "first\n")
            write(runs, "20261020T120905Z-1-2/j/h.jsonl", "second\n")
            self.assertEqual(dedupe_attempts.dedupe(runs), [])


if __name__ == "__main__":
    unittest.main()
