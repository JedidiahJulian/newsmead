"""Check the draft study-data example, not Android logging or real study packages.

Run from the repository root: python tools/gaze/check_study_data_example.py
Requires the locally installed jsonschema package. No app/device/network access.
The future multi-chunk manifest and production validator are separate work.
"""
from __future__ import annotations

import copy
import hashlib
import json
import math
from collections import Counter
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = ROOT / 'docs' / 'study-data'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def reject_constant(value):
    raise ValueError('Non-finite JSON constant: ' + value)


def check(records, validator):
    """Validate this self-contained, complete synthetic illustration."""
    require(bool(records), 'Empty example')
    seen = {}
    configs, bodies, layouts, views, deliveries, supports = {}, {}, {}, {}, set(), {}
    actions, presentations = {}, {}
    ref_keys = {'input_record_id', 'evaluation_record_id', 'decision_record_id', 'recovery_boundary_record_id'}
    refs_keys = {'input_record_ids', 'support_record_ids', 'loss_record_ids'}

    def check_refs(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key in ref_keys and item is not None:
                    require(item in seen, 'Missing/forward record reference: ' + item)
                elif key in refs_keys:
                    for reference in item:
                        require(reference in seen, 'Missing/forward record reference: ' + reference)
                else:
                    check_refs(item)
        elif isinstance(value, list):
            for item in value:
                check_refs(item)

    identity_keys = ('study_id', 'participant_id', 'installation_id', 'session_id')
    identity = tuple(records[0][k] for k in identity_keys)
    previous_time = -1
    for expected_seq, record in enumerate(records, 1):
        validator.validate(record)
        kind, p = record['kind'], record['payload']
        require(record['seq'] == expected_seq, 'Example sequence is not contiguous')
        require(record['record_id'] not in seen, 'Duplicate record ID')
        require(tuple(record[k] for k in identity_keys) == identity, 'Mixed session identity')
        require(record['recorded_elapsed_ms'] >= previous_time, 'Append time moved backwards')
        previous_time = record['recorded_elapsed_ms']
        check_refs(p)

        if kind == 'configuration':
            require(p['config_id'] not in configs, 'Configuration ID reused')
            configs[p['config_id']] = p
        elif kind == 'text_snapshot':
            require(hashlib.sha256(p['body_text'].encode('utf-8')).hexdigest() == p['body_sha256_utf8'], 'Body hash mismatch')
            bodies[p['article_snapshot_id']] = p['body_text']
        elif kind == 'layout_snapshot':
            require(p['article_snapshot_id'] in bodies, 'Missing body')
            text_length = len(bodies[p['article_snapshot_id']].encode('utf-16-le')) // 2
            require([x['line_index'] for x in p['lines']] == list(range(len(p['lines']))), 'Line index ordering')
            for item in p['lines'] + p['words']:
                word = item['range_utf16']
                require(0 <= word['start_utf16'] < word['end_utf16'] <= text_length, 'Invalid UTF-16 range')
                left, top, right, bottom = item['bounds_content_px']
                require(left <= right and top <= bottom, 'Reversed rectangle')
            layouts[p['layout_id']] = p
        elif kind == 'viewport':
            require(p['layout_id'] in layouts, 'Missing viewport layout')
            require(p['effective_elapsed_ms'] <= record['recorded_elapsed_ms'], 'Future viewport')
            views[p['viewport_id']] = p
        elif kind == 'gaze_observation':
            key = (p['provider_epoch_id'], p['delivery_id'])
            require(key not in deliveries, 'Duplicate scoped delivery ID')
            deliveries.add(key)
            capture = p['capture_elapsed_ms']
            require(capture is None or capture <= p['delivery_elapsed_ms'] <= record['recorded_elapsed_ms'], 'Example clock ordering')
            if p['eligible']:
                require(p['fresh_capture'] and p['selected_screen_px'] is not None and capture is not None, 'Eligible missing/nonfresh point')
                require(p['layout_id'] in layouts and p['capture_viewport_id'] in views, 'Missing eligible geometry')
                view = views[p['capture_viewport_id']]
                require(view['layout_id'] == p['layout_id'], 'Viewport layout mismatch')
                require(view['effective_elapsed_ms'] <= capture, 'Future capture geometry')
                if p['analysis_frame'] == 'auto_scroll_content_frame_v1':
                    origin = view['text_origin_screen_px']
                    wanted = [a-b for a, b in zip(p['selected_screen_px'], origin)]
                    require(p['analysis_point_px'] is not None and all(math.isclose(a, b, abs_tol=1e-6) for a, b in zip(wanted, p['analysis_point_px'])), 'Wrong content transform')
            else:
                require(p['exclusion'] is not None, 'Excluded observation without reason')
        elif kind == 'support_revision':
            key = (p['producer'], p['generation'], p['entity_id'])
            require(p['revision'] > supports.get(key, 0), 'Support revision did not advance')
            supports[key] = p['revision']
            require(p['layout_id'] in layouts, 'Missing support layout')
            first, last, confirmed = p['first_capture_ms'], p['last_capture_ms'], p['first_confirmed_capture_ms']
            require(first is None or last is None or first <= last, 'Support capture ordering')
            require(confirmed is None or first <= confirmed <= last, 'Support confirmation ordering')
            require(p['evidence_available_ms'] <= record['recorded_elapsed_ms'], 'Future evidence availability')
        elif kind == 'aid_evaluation':
            require(p['config_id'] in configs, 'Missing evaluation config')
            require({x['aid_channel'] for x in p['entries']} == {'guide', 'lookup', 'bookmark', 'focus'}, 'Missing/duplicate aid channel')
        elif kind == 'aid_decision':
            require(p['config_id'] in configs, 'Missing decision config')
            require(seen[p['evaluation_record_id']]['kind'] == 'aid_evaluation', 'Decision references wrong record kind')
            if p['action_id'] is not None:
                actions[p['action_id']] = p['aid_channel']
        elif kind == 'aid_presentation':
            if p['action_id'] is not None:
                require(actions.get(p['action_id']) == p['aid_channel'], 'Presentation action/channel mismatch')
            if p['decision_record_id'] is not None:
                decision = seen[p['decision_record_id']]
                require(decision['kind'] == 'aid_decision' and decision['payload']['action_id'] == p['action_id'], 'Presentation decision mismatch')
            pid = p['presentation_id']
            require(pid not in presentations or presentations[pid] == p['aid_channel'], 'Presentation channel changed')
            presentations[pid] = p['aid_channel']
            require(p['drawn_proxy'] == (p['state'] == 'draw_proxy'), 'Draw proxy state mismatch')
        elif kind == 'collector_health':
            require(p['persisted_through_seq'] < record['seq'], 'Example durability checkpoint includes future/self')
        seen[record['record_id']] = record

    require(records[0]['kind'] == 'session_start' and records[-1]['kind'] == 'session_end', 'Example closure')
    require(records[0]['payload']['is_synthetic'], 'Fixture must be explicitly synthetic')
    require(records[0]['payload']['initial_config_id'] in configs, 'Missing initial config')
    end = records[-1]['payload']
    require(end['record_count_including_end'] == len(records), 'Footer count mismatch')
    observations = sum(x['kind'] == 'gaze_observation' for x in records)
    require(end['observation_records_serialized'] == observations == end['provider_deliveries_seen'], 'Example observation reconciliation')
    losses = [x for x in records if x['kind'] == 'collector_loss']
    require(all(x['payload']['count_status'] == 'exact' for x in losses), 'Example must have exact losses')
    require(sum(x['payload']['count'] for x in losses) == end['records_dropped'], 'Example loss reconciliation')
    require(end['loss_record_ids'] == [x['record_id'] for x in losses], 'Example loss references')
    require(len(actions) == 2 and len(presentations) == 1, 'Fixture must illustrate action refresh without a second presentation')


def main():
    schema = json.loads((PACKAGE / 'record-schema-v0.1.json').read_text(encoding='utf-8'), parse_constant=reject_constant)
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    records = [json.loads(line, parse_constant=reject_constant) for line in (PACKAGE / 'example-session.jsonl').read_text(encoding='utf-8').splitlines()]
    check(records, validator)
    mutations = {
        'missing_reference': lambda rows: next(x for x in rows if x['kind'] == 'aid_decision')['payload'].__setitem__('evaluation_record_id', 'missing'),
        'wrong_body_hash': lambda rows: next(x for x in rows if x['kind'] == 'text_snapshot')['payload'].__setitem__('body_sha256_utf8', '0' * 64),
        'future_capture_geometry': lambda rows: next(x for x in rows if x['kind'] == 'viewport')['payload'].__setitem__('effective_elapsed_ms', 200000),
        'duplicate_aid_channel': lambda rows: next(x for x in rows if x['kind'] == 'aid_evaluation')['payload']['entries'][0].__setitem__('aid_channel', 'focus'),
        'invalid_utf16_range': lambda rows: next(x for x in rows if x['kind'] == 'layout_snapshot')['payload']['lines'][1]['range_utf16'].__setitem__('end_utf16', 9999),
        'verified_visibility_claim': lambda rows: next(x for x in rows if x['kind'] == 'aid_presentation')['payload'].__setitem__('verified_visibility', True),
    }
    for name, mutate in mutations.items():
        altered = copy.deepcopy(records)
        mutate(altered)
        try:
            check(altered, validator)
        except (ValueError, KeyError, ValidationError):
            continue
        raise ValueError('Invalid fixture was accepted: ' + name)

    manifest_schema = json.loads((PACKAGE / 'manifest-schema-v0.1.json').read_text(encoding='utf-8'))
    Draft202012Validator.check_schema(manifest_schema)
    manifest_validator = Draft202012Validator(manifest_schema, format_checker=FormatChecker())
    manifest = json.loads((PACKAGE / 'example-manifest.json').read_text(encoding='utf-8'))
    manifest_validator.validate(manifest)
    require(len(manifest['chunks']) == 1, 'Fixture checker expects one chunk')
    chunk = manifest['chunks'][0]
    data = (PACKAGE / chunk['file']).read_bytes()
    require(chunk['sha256'] == hashlib.sha256(data).hexdigest(), 'Chunk hash mismatch')
    require(chunk['byte_count'] == len(data), 'Chunk byte count mismatch')
    require(chunk['record_count'] == manifest['record_count'] == len(records), 'Manifest record count mismatch')
    require(chunk['first_seq'] == 1 and chunk['last_seq'] == manifest['highest_persisted_seq'] == records[-1]['seq'], 'Manifest sequence range')
    require(manifest['loss_record_ids'] == records[-1]['payload']['loss_record_ids'], 'Manifest loss references')
    require(manifest['dropped_records_known'] == records[-1]['payload']['records_dropped'] and not manifest['content_complete'], 'Manifest loss completeness')
    require(manifest['session_end_record_id'] == records[-1]['record_id'], 'Manifest end reference')
    for key in ('study_id', 'participant_id', 'installation_id', 'session_id'):
        require(manifest[key] == records[0][key], 'Manifest identity mismatch')
    # This checks only the recovery metadata's representability, not crash IO.
    recovered = copy.deepcopy(manifest)
    prefix_data = ''.join(json.dumps(x, ensure_ascii=False, separators=(',', ':'), allow_nan=False) + '\n' for x in records[:-1]).encode('utf-8')
    recovered.update({'status': 'recovered_incomplete', 'session_end_record_id': None,
                      'record_count': len(records) - 1, 'highest_persisted_seq': records[-2]['seq'],
                      'chunks': [{'file': 'in-memory-recovered-prefix.jsonl', 'first_seq': 1,
                                  'last_seq': records[-2]['seq'], 'record_count': len(records) - 1,
                                  'byte_count': len(prefix_data), 'sha256': hashlib.sha256(prefix_data).hexdigest(),
                                  'closure': 'valid_prefix_recovered'}],
                      'unknown_tail': True,
                      'recovery': {'original_clock_domain_id': manifest['clock_domain_id'],
                                   'recovered_in_clock_domain_id': 'SYNTHETIC-BOOT-2',
                                   'recovered_elapsed_ms': 2000, 'recovered_utc': '2026-10-22T03:00:00Z',
                                   'last_known_original_elapsed_ms': records[-2]['recorded_elapsed_ms'],
                                   'last_known_seq': records[-2]['seq'],
                                   'actual_end_original_elapsed_ms': None, 'reason': 'synthetic_reboot'}})
    manifest_validator.validate(recovered)
    print(json.dumps({'result': 'passed', 'scope': 'draft_schema_and_synthetic_example_only',
                      'records': len(records), 'record_kinds': dict(Counter(x['kind'] for x in records)),
                      'corruption_cases_rejected': list(mutations),
                      'manifest_integrity': 'passed',
                      'cross_boot_unknown_end_shape': 'passed'}, indent=2))


if __name__ == '__main__':
    main()
