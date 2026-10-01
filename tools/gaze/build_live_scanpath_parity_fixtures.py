"""Freeze synthetic raw-action prefixes from the Python reference for JVM replay.

No recordings are read. P01-P12 and common fixation fields are compared;
live P13 has an immediate boundary contract, tested separately on the JVM.
Run with --check to verify that the checked-in fixture still matches the reference.
"""
import argparse
import json
import math
from pathlib import Path
import re

from analyze_reading_measurement import FixationDetector
from scanpath_patterns import classify_patterns


ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / 'app/src/test/resources/scanpath/live-reference.tsv'
WORDS = [[i * 5, i * 5 + 4] for i in range(12)]
CONFIGS = {
    'default': {},
    'configured': dict(return_sweep_start_fraction=0.25, return_sweep_end_fraction=0.75,
                       word_residence_ms=150, line_residence_ms=250,
                       progression_window_ms=300, progression_min_observed_fraction=0.5,
                       word_progression_max_units_per_s=2, line_progression_max_units_per_s=1,
                       cycle_window_ms=2000),
    'declared_ltr': dict(return_sweep_start_fraction=0.25, return_sweep_end_fraction=0.75,
                         text_direction='ltr'),
}


def canonical(value):
    if value is None:
        return 'null'
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, (int, float)):
        if not math.isfinite(value):
            raise ValueError('Nonfinite fixture output')
        return format(value, '.8f').rstrip('0').rstrip('.') or '0'
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    if isinstance(value, dict):
        return '{' + ','.join(canonical(k) + ':' + canonical(value[k]) for k in sorted(value)) + '}'
    return '[' + ','.join(map(canonical, value)) + ']'


def geometry(at=0, snapshot='geometry:1', direction='ltr', origin=0):
    return ('geometry', at, snapshot, direction, origin)


def sample(at, x, line=0, word=0, delivery=True, eligible=True, snapshot='geometry:1', y=None):
    start, end = WORDS[word] if word is not None else (None, None)
    return ('sample', at, at + 7 if delivery else None, x, line * 20 + 10 if y is None else y,
            line, start, end, snapshot, eligible)


def paths(points, start=200, snapshot='geometry:1', delivery=True):
    return [sample(start + n * 150 + dt, x, line, word, delivery, snapshot=snapshot)
            for n, (word, line, x) in enumerate(points) for dt in (0, 50, 100)]


def cases():
    yield 'movements', 'configured', [geometry(), *paths([
        (0, 0, 10), (1, 0, 40), (4, 0, 90), (2, 0, 50), (2, 0, 75),
        (3, 1, 10), (9, 3, 85), (6, 2, 50)]), ('boundary', 'session_end', 1450)]
    yield 'word_line_cycles', 'configured', [geometry(), *paths([
        (0, 0, 90), (3, 1, 10), (0, 0, 90), (3, 1, 10), (4, 1, 40),
        (0, 0, 90), (2, 0, 30), (1, 0, 65), (4, 1, 10)]), ('boundary', 'session_end', 1600)]
    ambiguous = [sample(350, 40, word=1), sample(400, 40, word=2), sample(450, 40, word=3)]
    yield 'word_ambiguity_line_continuity', 'default', [geometry(), *paths([(0, 0, 10)]),
        *ambiguous, *paths([(4, 1, 10), (5, 1, 40)], start=500), ('boundary', 'session_end', 825)]
    ambiguous_line = [sample(350 + n * 50, 40, line=n, word=1, y=10) for n in range(3)]
    yield 'line_ambiguity_word_continuity', 'default', [geometry(), *paths([(0, 0, 10)]),
        *ambiguous_line, *paths([(2, 0, 75)], start=500), ('boundary', 'session_end', 650)]
    yield 'invalid_and_duplicate', 'default', [geometry(), *paths([(0, 0, 10), (1, 0, 40)]),
        sample(500, 40, eligible=False), sample(450, 75, word=2),
        *paths([(2, 0, 75), (3, 1, 10)], start=550), ('boundary', 'session_end', 875)]
    yield 'sample_gap', 'default', [geometry(), *paths([(0, 0, 10)]),
        *paths([(1, 0, 40), (2, 0, 75)], start=600), ('boundary', 'session_end', 925)]
    jitter = [sample(350 + n * 50, 40 if n % 2 else 80, word=1) for n in range(7)]
    yield 'unclassified_gap', 'default', [geometry(), *paths([(0, 0, 10)]), *jitter,
        *paths([(2, 0, 10)], start=700), ('boundary', 'session_end', 850)]
    yield 'geometry_change', 'configured', [geometry(), *paths([(0, 0, 90), (3, 1, 10)]),
        geometry(500, 'geometry:2', 'ltr', 100),
        *paths([(6, 2, 175), (9, 3, 110)], start=700, snapshot='geometry:2'),
        ('boundary', 'session_end', 1025)]
    yield 'rtl_cannot_be_overridden', 'declared_ltr', [geometry(direction='rtl'),
        *paths([(2, 0, 90), (3, 1, 10)]), ('boundary', 'session_end', 525)]
    yield 'unknown_direction', 'configured', [geometry(direction='unknown'),
        *paths([(2, 0, 90), (3, 1, 10)]), ('boundary', 'session_end', 525)]
    yield 'unset_thresholds', 'default', [geometry(), *paths([(2, 0, 90), (3, 1, 10)]),
        ('boundary', 'session_end', 525)]
    yield 'capture_lower_bound', 'configured', [geometry(),
        *paths([(0, 0, 10), (1, 0, 40), (2, 0, 75)], delivery=False),
        ('boundary', 'session_end', 675)]
    yield 'mixed_availability_basis', 'default', [geometry(),
        *paths([(0, 0, 10)], delivery=False), *paths([(1, 0, 40)], start=350),
        *paths([(2, 0, 75)], start=500, delivery=False), ('boundary', 'session_end', 675)]


def fixture():
    output = ['# Synthetic reference: analyzer v5 / classifier v1. Capture/delivery ms; UTF-16; raw px.',
              '# Canonical numbers rounded to 8 decimals. P13 excluded; episode group ID normalized.']
    for name, cfg, actions in cases():
        output.append('\t'.join(('case', name, cfg)))
        detector = FixationDetector()
        geometries = {}
        current = None
        for action in actions:
            output.append('\t'.join(str(v).lower() if isinstance(v, bool) else '-' if v is None else str(v)
                                    for v in action))
            if action[0] == 'geometry':
                _, at, current, direction, origin = action
                detector.boundary('geometry_change', at)
                geometries[current] = dict(
                    text_origin_x=origin, text_origin_y=0, line_height_px=20,
                    visible_rect_screen=[origin, 0, origin + 100, 100],
                    lines=[[i * 15, i * 15 + 15, 0, 100, i * 20, i * 20 + 20] for i in range(4)],
                )
                if direction != 'unknown':
                    geometries[current]['text_direction'] = direction
            elif action[0] == 'sample':
                _, at, delivery, x, y, line, start, end, snapshot, eligible = action
                row = dict(capture_ms=at, delivery_ms=delivery, raw_x=x, raw_y=y, line=line,
                           word_start=-1 if start is None else start, word_end=-1 if end is None else end,
                           geometry_snapshot_id=snapshot)
                if not eligible or snapshot != current:
                    available, basis = detector.availability(row)
                    detector.boundary('invalid_sample', at, evidence_available_ms=available,
                                      evidence_time_basis=basis)
                    detector.note_capture_time(at)
                else:
                    detector.add(row, 10)
            else:
                detector.boundary(action[1], action[2])
            fixations = [dict(index=i, **{k: v for k, v in f.items() if k != 'geometry_id'})
                         for i, f in enumerate(detector.fixations)]
            result = classify_patterns(detector.fixations, WORDS, detector.segment_boundaries,
                                       geometries=geometries, config=CONFIGS[cfg])
            patterns = []
            for event in result['events']:
                if event['pattern_id'] == 'P13':
                    continue
                normalized = {k: v for k, v in event.items() if k not in ('event_id', 'label')}
                normalized['metrics'] = dict(normalized['metrics'])
                episode = normalized['metrics'].get('episode_id')
                if episode:
                    normalized['metrics']['episode_id'] = re.sub(r'^(word|line)-\d+-', r'\1-episode-', episode)
                patterns.append(normalized)
            output.append('\t'.join(('check', canonical(fixations), canonical(patterns))))
        output.append('endcase')
    return '\n'.join(output) + '\n'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    expected = fixture()
    if args.check:
        if not DESTINATION.is_file() or DESTINATION.read_text(encoding='utf-8') != expected:
            raise SystemExit('Live parity fixture differs from Python reference; regenerate and review changes.')
        print('Live parity fixture matches Python reference (13 synthetic cases).')
    else:
        DESTINATION.parent.mkdir(parents=True, exist_ok=True)
        DESTINATION.write_text(expected, encoding='utf-8', newline='\n')
        print(f'Wrote 13 synthetic cases to {DESTINATION.relative_to(ROOT)}')
