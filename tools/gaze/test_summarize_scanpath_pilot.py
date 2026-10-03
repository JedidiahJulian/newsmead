"""Contract checks using software fixtures; no human validation."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from summarize_scanpath_pilot import INSTRUMENT, read_trace, summarize


def fixture():
    target = {"id": "word:0:4", "line": 0, "bounds_screen": {"left": 10, "right": 30}}
    details = [
        ("header", {"instrument": INSTRUMENT, "schema_version": 1,
                    "body_text": "test", "body_sha256": hashlib.sha256(b"test").hexdigest(),
                    "coordinate_units": "physical_screen_pixels", "offset_units": "UTF-16", "time_units": "milliseconds",
                    "input_kind": "synthetic_pilot_check", "plan": {"targets": [target]}}),
        ("prompt_onset_proxy", {"index": 0}),
        ("raw_observation", {"input_ordinal": 1, "phase": "task", "reason": "coordinate", "raw_x": 20}),
        ("sample", {"input_ordinal": 1}),
        ("input_acceptance", {"input_ordinal": 1, "engine_accepted": True}),
        ("footer", {"complete_trace": True, "trace_failure": None, "record_count_before_footer": 5,
                    "observations": 1, "engine_accepted_samples": 1, "terminal_deliveries": 0,
                    "termination": "completed", "score": {"total_samples": 1, "targets": [
                        {"index": 0, "target": target, "attempted": True, "eligible_samples": 0, "exact_word_given_eligible": None}]}})]
    return [{"sequence": i, "kind": kind, "detail": detail} for i, (kind, detail) in enumerate(details, 1)]


class PilotSummaryTests(unittest.TestCase):
    def test_complete_fixture_retains_null_denominators_and_omits_private_fields(self):
        result = summarize(fixture())
        self.assertTrue(result["integrity_ok"])
        text = json.dumps(result)
        for private_field in ("body_text", "bounds_screen", "raw_x"):
            self.assertNotIn(private_field, text)
        self.assertIsNone(result["score"]["targets"][0]["exact_word_given_eligible"])

    def test_partial_name_cannot_be_complete_even_with_complete_footer(self):
        self.assertIn("partial_filename", summarize(fixture(), final_filename=False)["issues"])

    def test_interrupted_trial_can_have_complete_audit(self):
        rows = fixture()
        rows[-1]["detail"]["termination"] = "activity_paused"
        self.assertTrue(summarize(rows)["integrity_ok"])

    def test_inconsistent_counts_sequence_hash_and_target_retention_reported(self):
        rows = copy.deepcopy(fixture())
        rows[1]["sequence"] = 9
        rows[0]["detail"]["body_text"] = "changed"
        rows[-1]["detail"]["observations"] = 8
        rows[-1]["detail"]["score"]["targets"] = []
        issues = summarize(rows)["issues"]
        for expected in ("noncontiguous_record_sequence", "body_hash", "footer_input_counts", "planned_target_retention", "prompt_onset_pairing"):
            self.assertIn(expected, issues)

    def test_terminal_observation_needs_sample_but_not_engine_acceptance(self):
        rows = fixture()
        rows[2]["detail"]["phase"] = "terminal_delivery"
        del rows[4]
        for i, row in enumerate(rows, 1):
            row["sequence"] = i
        rows[-1]["detail"].update(record_count_before_footer=4, engine_accepted_samples=0, terminal_deliveries=1)
        self.assertTrue(summarize(rows)["integrity_ok"])

    def test_missing_footer_and_sample_pairing_fail(self):
        rows = fixture()[:-1]
        rows[3]["detail"]["input_ordinal"] = 2
        issues = summarize(rows)["issues"]
        self.assertIn("missing_or_misplaced_footer", issues)
        self.assertIn("raw_sample_pairing", issues)

    def test_other_instrument_is_rejected(self):
        rows = fixture()
        rows[0]["detail"]["instrument"] = "reading_validation_v6"
        with self.assertRaises(ValueError):
            summarize(rows)

    def test_truncated_and_nonfinite_json_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            trace = Path(folder) / "test.jsonl.partial"
            for text in ('{"detail":', '{"detail":{"x":NaN}}'):
                trace.write_text(text, encoding="utf-8")
                with self.assertRaises(ValueError):
                    read_trace(trace)


if __name__ == "__main__":
    unittest.main()
