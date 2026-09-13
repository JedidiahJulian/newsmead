import unittest

from analyze_reading_measurement import FixationDetector, analyze, summarize


def sample(t, x=10, y=10, word=0, **extra):
    return dict(type='sample', capture_ms=t, raw_x=x, raw_y=y, word_start=word * 5,
                word_end=word * 5 + 4, measurement_valid=True, geometry_id=1,
                reason='coordinate', **extra)


def fixation(word, start, duration=120, segment=0, **extra):
    return dict(word_start=word * 5 if word is not None else None,
                word_end=word * 5 + 4 if word is not None else None,
                start_ms=start, end_ms=start + duration, duration_ms=duration,
                segment=segment, left_censored=False, right_censored=False, **extra)


WORDS = [[i * 5, i * 5 + 4] for i in range(20)]


class DetectorTests(unittest.TestCase):
    def test_same_line_multiple_fixations_not_one_line_visit(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120):
            detector.add(sample(t), 20)
        for t in (160, 200, 240, 280):
            detector.add(sample(t, x=100, word=1), 20)
        detector.boundary()
        self.assertEqual([120, 120], [f['duration_ms'] for f in detector.fixations])
        self.assertTrue(detector.fixations[0]['left_censored'])
        self.assertFalse(detector.fixations[0]['right_censored'])
        self.assertTrue(detector.fixations[1]['right_censored'])

    def test_no_bridging_missing_gaze(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 120, 1000, 1040, 1080, 1120):
            detector.add(sample(t), 20)
        detector.boundary()
        self.assertEqual([120, 120], [f['duration_ms'] for f in detector.fixations])
        self.assertNotEqual(detector.fixations[0]['segment'], detector.fixations[1]['segment'])

    def test_noise_is_not_a_fixation(self):
        detector = FixationDetector()
        for t in range(0, 500, 40):
            detector.add(sample(t, x=t), 20)
        detector.boundary()
        self.assertEqual([], detector.fixations)

    def test_word_ambiguity_retained(self):
        detector = FixationDetector()
        for t, word in zip((0, 40, 80, 120), (0, 0, 1, 1)):
            detector.add(sample(t, word=word), 20)
        detector.boundary()
        self.assertIsNone(detector.fixations[0]['word_start'])

    def test_duplicate_time_cannot_extend_fixation(self):
        detector = FixationDetector()
        for t in (0, 40, 80, 80, 120):
            detector.add(sample(t), 20)
        detector.boundary()
        self.assertEqual([], detector.fixations)


class MetricTests(unittest.TestCase):
    def test_multiple_fixations_on_word_are_one_visit(self):
        events = [fixation(0, 0), fixation(1, 160, 120), fixation(1, 320, 160), fixation(2, 520)]
        metrics, visits = summarize(events, WORDS)
        self.assertEqual(280, metrics['mean_observed_first_pass_gaze_duration_ms'])
        self.assertEqual(3, len(visits))
        self.assertEqual(2, metrics['eligible_word_transitions'])

    def test_regressions_use_document_order_including_line_wrap(self):
        # A line wrap has smaller screen x but a larger document word ordinal.
        metrics, _ = summarize([fixation(3, 0), fixation(4, 160), fixation(2, 320)], WORDS)
        self.assertEqual(1, metrics['regressions'])
        self.assertEqual(0.5, metrics['regression_probability'])

    def test_revisits_and_skipping_have_explicit_denominators(self):
        metrics, _ = summarize([fixation(0, 0), fixation(3, 160), fixation(0, 320)], WORDS)
        self.assertEqual(2, metrics['first_pass_skipped_words'])
        self.assertEqual(3, metrics['first_pass_eligible_words'])
        self.assertAlmostEqual(1 / 3, metrics['observed_rereading_proportion'])

    def test_gaps_and_ambiguous_fixations_break_transitions(self):
        for events in ([fixation(5, 0), fixation(1, 160, segment=1)],
                       [fixation(5, 0), fixation(None, 160), fixation(1, 320)]):
            metrics, _ = summarize(events, WORDS)
            self.assertEqual(0, metrics['eligible_word_transitions'])
            self.assertIsNone(metrics['regression_probability'])

    def test_final_visit_excluded_from_first_pass_duration(self):
        metrics, visits = summarize([fixation(0, 0), fixation(1, 160)], WORDS)
        self.assertIsNone(metrics['mean_observed_first_pass_gaze_duration_ms'])
        self.assertTrue(visits[-1]['right_censored'])


class RecordingTests(unittest.TestCase):
    def records(self):
        rows = [dict(type='session_start', schema_version=1, session_id='synthetic', word_ranges=WORDS),
                dict(type='geometry', geometry_id=1, line_height_px=40)]
        rows += [sample(t) for t in (0, 40, 80, 120)]
        rows += [dict(type='session_end', complete=True, dropped_records=0, sample_records=4)]
        return rows

    def test_raw_stream_used_not_display_coordinates(self):
        rows = self.records()
        for i, row in enumerate(rows):
            if row['type'] == 'sample':
                row.update(display_x=i * 500, display_y=i * 500)
        result = analyze(rows)
        self.assertEqual(1, result['metrics']['detected_fixations'])
        self.assertEqual(120, result['fixations'][0]['duration_ms'])
        self.assertEqual(0, result['metrics']['complete_fixations'])

    def test_incomplete_or_dropped_recording_rejected(self):
        with self.assertRaises(ValueError):
            analyze(self.records()[:-1])
        rows = self.records()
        rows[-1]['dropped_records'] = 1
        with self.assertRaises(ValueError):
            analyze(rows)
        self.assertFalse(analyze(rows, allow_incomplete=True)['source_complete'])

    def test_scroll_geometry_change_splits_candidate(self):
        rows = self.records()
        rows.insert(4, dict(type='geometry', geometry_id=2, line_height_px=40))
        self.assertEqual(0, analyze(rows)['metrics']['detected_fixations'])

    def test_nonfinite_sample_breaks_candidate(self):
        rows = self.records()
        rows[4]['raw_x'] = float('nan')
        self.assertEqual(0, analyze(rows)['metrics']['detected_fixations'])


if __name__ == '__main__':
    unittest.main()
