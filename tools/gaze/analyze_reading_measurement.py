"""Replay schema-1 natural-reading logs. Standard library only; no trained RII.

Example: python tools/gaze/analyze_reading_measurement.py recording.jsonl --output analysis.json
All thresholds are provisional engineering parameters, retained in the output.
"""
import argparse
from collections import Counter
import json
import math
from pathlib import Path
import statistics


def dispersion(samples):
    return (max(s['raw_x'] for s in samples) - min(s['raw_x'] for s in samples)
            + max(s['raw_y'] for s in samples) - min(s['raw_y'] for s in samples))


class FixationDetector:
    """I-DT: expand a compact minimum-duration window until dispersion exceeds its limit."""
    def __init__(self, min_ms=100.0, min_samples=3, max_gap_ms=125.0, agreement=0.8):
        self.min_ms = min_ms
        self.min_samples = min_samples
        self.max_gap_ms = max_gap_ms
        self.agreement = agreement
        self.pending = []
        self.fixations = []
        self.segment = 0
        self.left_censored = True
        self.last_time = None

    def established(self):
        return (len(self.pending) >= self.min_samples and
                self.pending[-1]['capture_ms'] - self.pending[0]['capture_ms'] >= self.min_ms)

    def emit(self, right_censored):
        if not self.established():
            return
        samples = self.pending
        counts = Counter((s['word_start'], s['word_end']) for s in samples)
        word, count = counts.most_common(1)[0]
        assigned = word[0] >= 0 and word[1] > word[0] and count / len(samples) >= self.agreement
        self.fixations.append(dict(
            segment=self.segment, start_ms=samples[0]['capture_ms'], end_ms=samples[-1]['capture_ms'],
            duration_ms=samples[-1]['capture_ms'] - samples[0]['capture_ms'],
            sample_count=len(samples), x=statistics.mean(s['raw_x'] for s in samples),
            y=statistics.mean(s['raw_y'] for s in samples), dispersion_px=dispersion(samples),
            word_start=word[0] if assigned else None, word_end=word[1] if assigned else None,
            word_sample_agreement=count / len(samples), left_censored=self.left_censored,
            right_censored=right_censored,
        ))

    def boundary(self):
        self.emit(right_censored=True)
        self.pending = []
        self.last_time = None
        self.segment += 1
        self.left_censored = True

    def add(self, sample, threshold_px):
        timestamp = sample['capture_ms']
        if self.last_time is not None:
            if timestamp <= self.last_time:
                self.boundary()
                return  # Duplicate/out-of-order input cannot create an event.
            if timestamp - self.last_time > self.max_gap_ms:
                self.boundary()
        self.last_time = timestamp
        if self.pending and dispersion(self.pending + [sample]) > threshold_px:
            if self.established():
                self.emit(right_censored=False)
                self.pending = []
                self.left_censored = False
            else:
                while self.pending and dispersion(self.pending + [sample]) > threshold_px:
                    self.pending.pop(0)
                    self.left_censored = False
        self.pending.append(sample)


def summarize(fixations, word_ranges):
    """Only contiguous, unambiguous word sequences contribute transition/visit measures."""
    ordinals = {tuple(word): i for i, word in enumerate(word_ranges)}
    visits = []
    previous_segment = None
    group = 0
    for fixation in fixations:
        word = ordinals.get((fixation['word_start'], fixation['word_end']))
        if fixation['segment'] != previous_segment:
            group += 1
        previous_segment = fixation['segment']
        if word is None:
            group += 1
            continue
        if visits and visits[-1]['group'] == group and visits[-1]['word'] == word:
            visit = visits[-1]
            visit['duration_ms'] += fixation['duration_ms']
            visit['end_ms'] = fixation['end_ms']
            visit['right_censored'] = fixation['right_censored']
        else:
            if visits and visits[-1]['group'] != group:
                visits[-1]['right_censored'] = True
            visits.append(dict(group=group, word=word, duration_ms=fixation['duration_ms'],
                               start_ms=fixation['start_ms'], end_ms=fixation['end_ms'],
                               left_censored=fixation['left_censored'] or not visits or visits[-1]['group'] != group,
                               right_censored=fixation['right_censored']))

    if visits:
        visits[-1]['right_censored'] = True

    transitions = regressions = skipped = eligible = 0
    reread_ms = total_ms = 0.0
    first_pass = []
    seen = set()
    frontier = previous_word = None
    previous_group = None
    for visit in visits:
        if visit['group'] != previous_group:
            seen = set()
            frontier = previous_word = None
        previous_group = visit['group']
        word = visit['word']
        visit['revisit_within_segment'] = word in seen
        visit['first_pass_eligible'] = (previous_word is not None and word > frontier and
                                        not visit['left_censored'] and not visit['right_censored'])
        if visit['first_pass_eligible']:
            first_pass.append(visit['duration_ms'])
        if previous_word is not None:
            transitions += 1
            regressions += word < previous_word
            # Count initial forward coverage only from the known frontier, not after gaps
            # or forward movements from a rereading region. The landing word is included.
            if word > frontier and previous_word == frontier:
                skipped += word - previous_word - 1
                eligible += word - previous_word
        total_ms += visit['duration_ms']
        if word in seen:
            reread_ms += visit['duration_ms']
        seen.add(word)
        frontier = word if frontier is None else max(frontier, word)
        previous_word = word

    durations = [f['duration_ms'] for f in fixations if not f['left_censored'] and not f['right_censored']]
    ratio = lambda a, b: a / b if b else None
    return dict(
        detected_fixations=len(fixations), complete_fixations=len(durations),
        ambiguous_word_fixations=sum(f['word_start'] is None for f in fixations),
        mean_fixation_duration_ms=statistics.mean(durations) if durations else None,
        median_fixation_duration_ms=statistics.median(durations) if durations else None,
        first_pass_eligible_visits=len(first_pass),
        mean_observed_first_pass_gaze_duration_ms=statistics.mean(first_pass) if first_pass else None,
        observed_word_fixation_time_ms=total_ms, observed_revisit_fixation_time_ms=reread_ms,
        observed_rereading_proportion=ratio(reread_ms, total_ms),
        eligible_word_transitions=transitions, regressions=regressions,
        regression_probability=ratio(regressions, transitions),
        first_pass_skipped_words=skipped, first_pass_eligible_words=eligible,
        first_pass_skipping_rate=ratio(skipped, eligible),
    ), visits


def analyze(records, dispersion_lines=0.5, min_ms=100.0, min_samples=3,
            max_gap_ms=125.0, agreement=0.8, allow_incomplete=False):
    if not (dispersion_lines > 0 and min_ms > 0 and max_gap_ms > 0 and min_samples >= 2 and 0.5 < agreement <= 1):
        raise ValueError('Invalid detector parameters')
    if not records or records[0].get('type') != 'session_start' or records[0].get('schema_version') != 1:
        raise ValueError('Expected a schema-1 reading measurement session')
    metadata = records[0]
    if sum(r.get('type') == 'session_start' for r in records) != 1:
        raise ValueError('Analyze one session at a time')
    sample_count = sum(r.get('type') == 'sample' for r in records)
    end = records[-1]
    complete = (end.get('type') == 'session_end' and end.get('complete') is True
                and end.get('dropped_records') == 0 and end.get('sample_records') == sample_count)
    if not complete and not allow_incomplete:
        raise ValueError('Missing/incomplete footer or record loss; use --allow-incomplete for diagnostics only')
    detector = FixationDetector(min_ms, min_samples, max_gap_ms, agreement)
    geometry = None
    valid_count = 0
    reasons = Counter()
    exclusions = Counter()
    intervals = []
    last_capture = None
    for row in records[1:]:
        if row['type'] == 'geometry':
            detector.boundary()
            geometry = row
        elif row['type'] == 'sample':
            reasons[row['reason']] += 1
            if row.get('measurement_exclusion'):
                exclusions[row['measurement_exclusion']] += 1
            capture = row.get('capture_ms')
            if isinstance(capture, (int, float)) and math.isfinite(capture):
                if last_capture is not None and capture > last_capture:
                    intervals.append(capture - last_capture)
                last_capture = capture if last_capture is None else max(capture, last_capture)
            finite = all(isinstance(row.get(k), (int, float)) and math.isfinite(row[k])
                         for k in ('capture_ms', 'raw_x', 'raw_y'))
            if (not row.get('measurement_valid') or not finite or geometry is None
                    or row.get('geometry_id') != geometry['geometry_id']
                    or geometry['line_height_px'] <= 0):
                detector.boundary()
                continue
            valid_count += 1
            detector.add(row, dispersion_lines * geometry['line_height_px'])
        elif row['type'] == 'session_end':
            detector.boundary()
    detector.boundary()
    metrics, visits = summarize(detector.fixations, metadata['word_ranges'])
    return dict(
        analysis_version=1, session_id=metadata['session_id'], source_complete=complete,
        source_metadata=metadata,
        parameters=dict(dispersion_lines=dispersion_lines, min_ms=min_ms, min_samples=min_samples,
                        max_gap_ms=max_gap_ms, word_sample_agreement=agreement),
        sample_records=sample_count, valid_sample_records=valid_count, reasons=dict(reasons),
        measurement_exclusions=dict(exclusions),
        median_capture_interval_ms=statistics.median(intervals) if intervals else None,
        p95_capture_interval_ms=sorted(intervals)[math.ceil(0.95 * len(intervals)) - 1] if intervals else None,
        metrics=metrics, fixations=detector.fixations, visits=visits,
        interpretation='Provisional gaze measurements; no RII, attention diagnosis, or independent labels.',
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('recording', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--dispersion-lines', type=float, default=0.5)
    parser.add_argument('--min-ms', type=float, default=100.0)
    parser.add_argument('--min-samples', type=int, default=3)
    parser.add_argument('--max-gap-ms', type=float, default=125.0)
    parser.add_argument('--agreement', type=float, default=0.8)
    parser.add_argument('--allow-incomplete', action='store_true')
    args = parser.parse_args()
    with args.recording.open(encoding='utf-8') as source:
        records = [json.loads(line) for line in source if line.strip()]
    result = analyze(records, args.dispersion_lines, args.min_ms, args.min_samples,
                     args.max_gap_ms, args.agreement, args.allow_incomplete)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    print(json.dumps(result['metrics'], indent=2))


if __name__ == '__main__':
    main()
