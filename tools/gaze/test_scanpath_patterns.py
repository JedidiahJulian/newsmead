"""Synthetic structural tests; these do not validate human gaze or difficulty."""
import json
import unittest

from scanpath_patterns import classify_patterns


WORDS = [(i * 5, i * 5 + 4) for i in range(12)]


def fix(word, line, start, *, x=100, y=100, segment=0, geometry="g1",
        censored=False, available=None):
    return dict(segment=segment, start_ms=start, end_ms=start + 100,
                duration_ms=100, word_start=WORDS[word][0] if word is not None else None,
                word_end=WORDS[word][1] if word is not None else None,
                line=line, x=x, y=y, geometry_snapshot_id=geometry,
                left_censored=censored, right_censored=censored,
                evidence_available_ms=available, evidence_time_basis="delivery" if available else None)


def events(result, pid, resolution=None):
    return [e for e in result["events"] if e["pattern_id"] == pid and
            (resolution is None or e["resolution"] == resolution)]


class PatternTests(unittest.TestCase):
    def classify(self, fixes, **kwargs):
        result = classify_patterns(fixes, WORDS, **kwargs)
        json.dumps(result, allow_nan=False)
        return result

    def test_word_movements_and_refix_vs_revisit(self):
        fixes = [fix(w, 0, i * 150) for i, w in enumerate([0, 1, 3, 2, 2, 1, 2])]
        result = self.classify(fixes)
        self.assertEqual([e["metrics"]["delta_words"] for e in events(result, "P01")], [1, 1])
        self.assertEqual(len(events(result, "P02")), 1)
        self.assertEqual(len(events(result, "P03")), 2)
        self.assertEqual(len(events(result, "P04")), 1)
        self.assertEqual([e["metrics"]["phase"] for e in events(result, "P10", "word")],
                         ["revisit", "approaching_frontier"])
        self.assertEqual(events(result, "P10", "word")[0]["fixation_indices"], [1, 2, 5])

    def test_line_movements_and_separate_unknown(self):
        fixes = [fix(0, 0, 0), fix(None, 1, 150), fix(3, 3, 300),
                 fix(2, 2, 450), fix(1, 2, 600)]
        result = self.classify(fixes)
        self.assertEqual(len(events(result, "P05")), 1)
        self.assertEqual(len(events(result, "P06")), 1)
        self.assertEqual(len(events(result, "P08")), 1)
        self.assertEqual(len(events(result, "P07")), 1)
        self.assertTrue(any(e["metrics"]["reason"] == "unassigned_word"
                            for e in events(result, "P13", "word")))
        self.assertFalse(any(e["metrics"]["reason"] == "unassigned_line"
                             for e in events(result, "P13", "line")))
        self.assertFalse(events(result, "P02"))

    def test_cycles_both_resolutions_and_gap_reset(self):
        fixes = [fix(w, line, i * 150) for i, (w, line) in
                 enumerate([(0, 0), (1, 1), (0, 0), (1, 1)])]
        result = self.classify(fixes)
        self.assertEqual(len(events(result, "P11", "word")), 1)
        self.assertEqual(len(events(result, "P11", "line")), 1)
        self.assertFalse(events(self.classify(fixes, config={"cycle_window_ms": 300}), "P11"))
        fixes[2]["start_ms"] = 1000
        fixes[2]["end_ms"] = 1100
        fixes[3]["start_ms"] = 1150
        fixes[3]["end_ms"] = 1250
        self.assertFalse(events(self.classify(fixes), "P11"))

    def test_sweep_geometry_and_undershoot(self):
        geo = {"g1": dict(schema_version=1, text_origin_x=100, text_origin_y=200,
                          text_direction='ltr',
                          visible_rect_screen=[100, 200, 500, 300],
                          lines=[[0, 20, 0, 200, 0, 40], [20, 40, 0, 200, 40, 80]])}
        fixes = [fix(0, 0, 0, x=280, y=220), fix(1, 1, 150, x=170, y=260)]
        config = {"return_sweep_end_fraction": .8, "return_sweep_start_fraction": .4}
        result = self.classify(fixes, geometries=geo, config=config)
        sweep = events(result, "P09")[0]
        self.assertEqual(sweep["metrics"]["status"], "observed")
        self.assertAlmostEqual(sweep["metrics"]["from_end_fraction"], .9)
        self.assertAlmostEqual(sweep["metrics"]["to_start_fraction"], .35)
        self.assertEqual(result["matched_counts"]["P09"], 1)
        self.assertEqual(events(self.classify(fixes), "P09")[0]["metrics"]["status"], "abstain")
        self.assertNotIn("P09", self.classify(fixes)["matched_counts"])
        self.assertEqual(self.classify(fixes)["pattern_status"]["P09"]["status"], "not_configured")
        self.assertEqual(events(self.classify(fixes, geometries=geo, config=config), "P09")[0]
                         ["metrics"]["reason"], None)
        fixes[1]["x"] = 260
        self.assertEqual(events(self.classify(fixes, geometries=geo, config=config), "P09")[0]
                         ["metrics"]["status"], "not_matched")
        fixes[1]["x"] = 90
        self.assertEqual(events(self.classify(fixes, geometries=geo, config=config), "P09")[0]
                         ["metrics"]["reason"], "outside_visible_rect")
        fixes[1]["x"] = 310
        self.assertEqual(events(self.classify(fixes, geometries=geo, config=config), "P09")[0]
                         ["metrics"]["reason"], "outside_line_geometry")
        fixes[1]["x"] = 170
        geo["g1"]["text_direction"] = "rtl"
        self.assertEqual(events(self.classify(fixes, geometries=geo, config=config), "P09")[0]
                         ["metrics"]["reason"], "direction_unsupported")
        geo["g1"]["text_direction"] = "ltr"
        geo["g1"]["lines"] = 5
        self.assertEqual(events(self.classify(fixes, geometries=geo, config=config), "P09")[0]
                         ["metrics"]["reason"], "line_geometry_invalid")
        fixes[1]["geometry_snapshot_id"] = "g2"
        self.assertFalse(events(self.classify(fixes, geometries=geo, config=config), "P09"))

    def test_residence_availability_boundary_and_prefix(self):
        fixes = [fix(0, 0, 0, censored=True), fix(0, 0, 200), fix(1, 1, 400, available=510)]
        result = self.classify(fixes, segment_boundaries=[dict(from_segment=0,
                                to_segment=1, reason="calibration_change", at_ms=600,
                                evidence_available_ms=625, evidence_time_basis="delivery")])
        residence = [e for e in events(result, "P12", "word") if
                     e["metrics"]["kind"] == "residence_update"]
        self.assertEqual([e["metrics"]["observed_span_ms"] for e in residence], [100, 200, 100])
        self.assertEqual(residence[1]["metrics"]["elapsed_ms"], 300)
        self.assertIsNone(residence[0]["evidence_available_ms"])
        self.assertIsNone(residence[1]["metrics"]["long_flag"])
        self.assertEqual(len([e for e in events(result, "P13") if e["metrics"].get("terminal")]), 2)
        self.assertTrue(all(e["evidence_available_ms"] == 625 for e in events(result, "P13")
                            if e["metrics"].get("terminal")))
        prefix = self.classify(fixes[:2])
        self.assertEqual(prefix["events"], [e for e in result["events"] if
                         all(i < 2 for i in e["fixation_indices"]) and
                         not e["metrics"].get("terminal")])

    def test_config_validation_and_explicit_flags(self):
        f = [fix(0, 0, 0)]
        for config in ({"word_residence_ms": True}, {"word_residence_ms": float("inf")},
                       {"return_sweep_start_fraction": .1},
                       {"return_sweep_start_fraction": .8, "return_sweep_end_fraction": .7},
                       {"word_progression_max_units_per_s": 1},
                       {"word_progression_max_units_per_s": 1, "progression_window_ms": 100},
                       {"progression_min_observed_fraction": 2}):
            with self.subTest(config=config), self.assertRaises(ValueError):
                self.classify(f, config=config)
        result = self.classify(f, config={"word_residence_ms": 90})
        self.assertTrue(next(e for e in events(result, "P12", "word") if
                             e["metrics"]["kind"] == "residence_update")["metrics"]["long_flag"])

    def test_sliding_progression_uses_observed_anchor_frontier(self):
        fixes = [fix(i, 0, i * 150) for i in range(5)]
        result = self.classify(fixes, config={"progression_window_ms": 300,
                                               "progression_min_observed_fraction": .5,
                                               "word_progression_max_units_per_s": 10})
        progress = [e for e in events(result, "P12", "word") if
                    e["metrics"]["kind"] == "frontier_progression"]
        last = progress[-1]["metrics"]
        self.assertEqual(last["window_baseline_frontier"], 2)
        self.assertEqual(last["frontier_gain_units"], 2)
        self.assertEqual(last["slow_flag"], True)

    def test_frontier_episode_retains_intermediate_evidence(self):
        result = self.classify([fix(w, w, i * 150) for i, w in enumerate([0, 3, 0, 1, 3])])
        for resolution in ('word', 'line'):
            reached = [e for e in events(result, 'P10', resolution)
                       if e['metrics']['phase'] == 'frontier_reached']
            self.assertEqual([0, 1, 2, 3, 4], reached[-1]['fixation_indices'])

    def test_backward_episode_step_is_not_approaching_frontier(self):
        result = self.classify([fix(w, w, i * 150) for i, w in enumerate([0, 5, 0, 3, 2])])
        for resolution in ('word', 'line'):
            phases = [e['metrics']['phase'] for e in events(result, 'P10', resolution)]
            self.assertEqual(['revisit', 'approaching_frontier', 'backward_within_episode'], phases)
            last = events(result, 'P10', resolution)[-1]
            self.assertEqual(3, last['metrics']['previous_ordinal'])
            self.assertEqual(2, last['metrics']['current_ordinal'])

    def test_sweep_requires_direction_and_explicit_rtl_cannot_be_overridden(self):
        geo = {'g1': dict(text_origin_x=100, text_origin_y=200,
                         visible_rect_screen=[100, 200, 500, 300],
                         lines=[[0, 20, 0, 200, 0, 40], [20, 40, 0, 200, 40, 80]])}
        fixes = [fix(0, 0, 0, x=280, y=220), fix(1, 1, 150, x=170, y=260)]
        config = {'return_sweep_end_fraction': .8, 'return_sweep_start_fraction': .4}
        result = self.classify(fixes, geometries=geo, config=config)
        self.assertEqual('direction_unknown', events(result, 'P09')[0]['metrics']['reason'])
        self.assertNotIn('P09', result['matched_counts'])
        config['text_direction'] = 'ltr'
        self.assertEqual('observed', events(self.classify(fixes, geometries=geo, config=config),
                                            'P09')[0]['metrics']['status'])
        geo['g1']['text_direction'] = 'rtl'
        self.assertEqual('direction_unsupported',
                         events(self.classify(fixes, geometries=geo, config=config),
                                'P09')[0]['metrics']['reason'])

    def test_censored_below_threshold_residence_is_not_classified_short(self):
        for resolution, key in (('word', 'word_residence_ms'), ('line', 'line_residence_ms')):
            for side in ('left_censored', 'right_censored'):
                with self.subTest(resolution=resolution, side=side):
                    candidate = fix(0, 0, 0)
                    candidate[side] = True
                    result = self.classify([candidate], config={key: 200})
                    residence = next(e for e in events(result, 'P12', resolution)
                                     if e['metrics']['kind'] == 'residence_update')
                    self.assertIsNone(residence['metrics']['long_flag'])
                    self.assertFalse(residence['metrics']['observed_threshold_crossed'])
                    result = self.classify([candidate], config={key: 90})
                    residence = next(e for e in events(result, 'P12', resolution)
                                     if e['metrics']['kind'] == 'residence_update')
                    self.assertTrue(residence['metrics']['long_flag'])

    def test_configuration_type_and_direction_are_validated(self):
        for config in ([], True, {'text_direction': True}, {'text_direction': 'unknown'},
                       {1: 5}):
            with self.subTest(config=config), self.assertRaises(ValueError):
                self.classify([], config=config)

    def test_frontier_before_revisited_word_keeps_evidence_in_capture_order(self):
        result = self.classify([fix(w, w, i * 150) for i, w in enumerate([5, 2, 1, 2])])
        for resolution in ('word', 'line'):
            revisits = [e for e in events(result, 'P10', resolution)
                        if e['metrics']['phase'] == 'revisit']
            self.assertEqual([0, 1, 3], revisits[-1]['fixation_indices'])
            self.assertEqual(5, revisits[-1]['metrics']['remembered_frontier'])


if __name__ == "__main__":
    unittest.main()
