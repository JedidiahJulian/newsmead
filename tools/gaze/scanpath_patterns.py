"""Pure offline descriptions of candidate-fixation scanpaths (schema 1).

Events describe assigned observations, never reading difficulty or eye accuracy.
Every emitted event is a snapshot of its supporting prefix; no final visit is
retroactively extended. Coordinates are raw screen pixels and offsets UTF-16.
"""

from collections import Counter
import math


OLIVIER = "https://doi.org/10.16910/jemr.15.4.5"
YAMAYA = "https://doi.org/10.2197/ipsjjip.25.100"
PARKER = "https://doi.org/10.1016/j.visres.2018.12.007"
MALSBURG = "https://doi.org/10.1016/j.jml.2011.02.004"

REGISTRY = {
    "P01": ("word_forward_step", "word", [OLIVIER]),
    "P02": ("word_forward_jump", "word", [OLIVIER]),
    "P03": ("word_backward_return", "word", [OLIVIER]),
    "P04": ("same_word_refixation_candidate", "word", [OLIVIER]),
    "P05": ("next_line_progression", "line", [YAMAYA]),
    "P06": ("forward_multiline_landing", "line", [YAMAYA]),
    "P07": ("same_line_backward_return", "word", [YAMAYA]),
    "P08": ("earlier_line_return", "line", [YAMAYA]),
    "P09": ("return_sweep_candidate", "line", [PARKER, YAMAYA]),
    "P10": ("revisit_frontier_episode", "word_or_line", [YAMAYA, MALSBURG]),
    "P11": ("repeated_cycle", "word_or_line", [MALSBURG]),
    "P12": ("observed_residence_progression", "word_or_line", []),
    "P13": ("unknown_or_break", "word_or_line", []),
}

CONFIG_KEYS = {
    "return_sweep_end_fraction", "return_sweep_start_fraction", "cycle_window_ms",
    "word_residence_ms", "line_residence_ms", "progression_window_ms",
    "word_progression_max_units_per_s", "line_progression_max_units_per_s",
    "progression_min_observed_fraction",
    "text_direction",
}


def _number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def _config(config):
    if config is not None and not isinstance(config, dict):
        raise ValueError('Pattern configuration must be a JSON object')
    config = {} if config is None else dict(config)
    if not all(isinstance(key, str) for key in config):
        raise ValueError('Pattern configuration keys must be strings')
    unknown = set(config) - CONFIG_KEYS
    if unknown:
        raise ValueError(f"Unknown pattern config: {sorted(unknown)}")
    for key, value in config.items():
        if key == 'text_direction':
            if not isinstance(value, str) or value not in ('ltr', 'rtl'):
                raise ValueError('text_direction must be ltr or rtl for the verified study condition')
            continue
        if not _number(value):
            raise ValueError(f"{key} must be a finite number")
        if key.endswith("_ms") and value <= 0:
            raise ValueError(f"{key} must be positive milliseconds")
        if key.endswith("_units_per_s") and value < 0:
            raise ValueError(f"{key} must be nonnegative units/s")
    start = config.get("return_sweep_start_fraction")
    end = config.get("return_sweep_end_fraction")
    if (start is None) != (end is None):
        raise ValueError("Both return-sweep endpoint fractions are required")
    if start is not None and not 0 <= start < end <= 1:
        raise ValueError("Require 0 <= start fraction < end fraction <= 1")
    coverage = config.get("progression_min_observed_fraction")
    if coverage is not None and not 0 < coverage <= 1:
        raise ValueError("Observed fraction must be in (0, 1]")
    if any(k in config for k in ("word_progression_max_units_per_s",
                                  "line_progression_max_units_per_s")):
        if "progression_window_ms" not in config or coverage is None:
            raise ValueError("Slow progression needs explicit window and minimum observed fraction")
    return config


def _availability(fixations, indices):
    values = [fixations[i].get("evidence_available_ms") for i in indices]
    return max(values) if values and all(_number(v) for v in values) else None


def _line_region(fixation, geometries, configured_direction=None):
    """Return normalized horizontal location on the matching physical line."""
    if not isinstance(geometries, dict):
        return None, "geometry_missing"
    gid = fixation.get("geometry_snapshot_id") or fixation.get("geometry_id")
    geometry = geometries.get(gid) if gid is not None else None
    if not isinstance(geometry, dict):
        return None, "geometry_missing"
    if geometry.get("schema") not in (None, 1) or geometry.get("schema_version", 1) != 1:
        return None, "geometry_schema_unsupported"
    direction = geometry.get('text_direction', configured_direction)
    if direction is None:
        return None, 'direction_unknown'
    if direction not in ("ltr", "LTR"):
        return None, "direction_unsupported"
    origin_x, origin_y = geometry.get("text_origin_x"), geometry.get("text_origin_y")
    x, y = fixation.get("x"), fixation.get("y")
    if not all(_number(v) for v in (origin_x, origin_y, x, y)):
        return None, "coordinate_missing"
    rect = geometry.get("visible_rect_screen")
    if (not isinstance(rect, (list, tuple)) or len(rect) != 4 or
            not all(_number(v) for v in rect) or rect[2] <= rect[0] or rect[3] <= rect[1]):
        return None, "visible_rect_missing"
    if not (rect[0] <= x <= rect[2] and rect[1] <= y <= rect[3]):
        return None, "outside_visible_rect"
    line = fixation.get("line")
    lines = geometry.get("lines", ())
    if not isinstance(lines, (list, tuple)):
        return None, "line_geometry_invalid"
    if not isinstance(line, int) or isinstance(line, bool) or line < 0 or line >= len(lines):
        return None, "line_geometry_missing"
    row = lines[line]
    if not isinstance(row, (list, tuple)) or len(row) != 6 or not all(_number(v) for v in row):
        return None, "line_geometry_invalid"
    start, end, left, right, top, bottom = row
    if end <= start or right <= left or bottom <= top:
        return None, "line_geometry_invalid"
    local_x, local_y = x - origin_x, y - origin_y
    if not top <= local_y <= bottom or not left <= local_x <= right:
        return None, "outside_line_geometry"
    return (local_x - left) / (right - left), None


def classify_patterns(fixations, word_ranges, segment_boundaries=(), geometries=None,
                      config=None, max_transition_gap_ms=125.0):
    """Classify finalized candidate fixations in supplied capture order.

    ``evidence_available_ms`` is used only if supplied for every contributor;
    candidate ``end_ms`` is never substituted as an emission timestamp.
    """
    cfg = _config(config)
    if not _number(max_transition_gap_ms) or max_transition_gap_ms < 0:
        raise ValueError("max_transition_gap_ms must be nonnegative milliseconds")
    ordinals = {tuple(pair): i for i, pair in enumerate(word_ranges)}
    events = []
    counts = Counter()
    boundaries = {b["from_segment"]: b for b in segment_boundaries}
    state = {r: dict(previous=None, visits=[], seen={}, frontier=None,
                     frontier_support=None, episode=None, visit=None, group=0)
             for r in ("word", "line")}

    def emit(pid, resolution, indices, metrics=None, limitations=()):
        indices = sorted(set(indices))
        source = [fixations[j] for j in indices]
        event = dict(event_id=f'pattern:{len(events) + 1}',
                     pattern_id=pid, label=REGISTRY[pid][0], resolution=resolution,
                     fixation_indices=indices,
                     start_ms=min(f["start_ms"] for f in source) if source else None,
                     end_ms=max(f["end_ms"] for f in source) if source else None,
                     evidence_available_ms=_availability(fixations, indices),
                     evidence_time_basis=(source[-1].get("evidence_time_basis")
                                          if source and all(f.get("evidence_time_basis") ==
                                                            source[-1].get("evidence_time_basis") for f in source)
                                          else None),
                     metrics=metrics or {}, limitations=list(limitations))
        events.append(event)
        counts[pid] += 1
        return event

    def break_path(resolution, reason, before=None, after=None, at_ms=None):
        st = state[resolution]
        indices = [j for j in (before, after) if j is not None]
        event = emit("P13", resolution, indices, dict(reason=reason, at_ms=at_ms,
                          from_fixation_index=before, to_fixation_index=after))
        if at_ms is not None:
            event["end_ms"] = at_ms
        st.update(previous=None, visits=[], seen={}, frontier=None,
                  frontier_support=None, episode=None, visit=None, group=st["group"] + 1)

    def update_residence(resolution, index, ordinal, fix):
        st = state[resolution]
        visit = st["visit"]
        if visit is None or visit["ordinal"] != ordinal:
            visit = dict(ordinal=ordinal, indices=[], observed_ms=0.0,
                         start_ms=fix["start_ms"], left_censored=bool(fix.get("left_censored")))
            st["visit"] = visit
        visit["indices"].append(index)
        visit["observed_ms"] += fix["duration_ms"]
        elapsed = fix["end_ms"] - visit["start_ms"]
        threshold = cfg.get(f"{resolution}_residence_ms")
        span = visit["observed_ms"]
        emit("P12", resolution, visit["indices"],
             dict(kind="residence_update", ordinal=ordinal, observed_span_ms=span,
                  elapsed_ms=elapsed, observed_fraction=span / elapsed if elapsed > 0 else None,
                  left_censored=visit["left_censored"],
                  right_censored=bool(fix.get("right_censored")),
                  observed_threshold_crossed=(span >= threshold) if threshold is not None else None,
                  long_flag=True if threshold is not None and span >= threshold else None),
             ["candidate_fixation_spans_only", "lower_bound_if_censored"])

    def update_progression(resolution, index, ordinal, fix):
        st = state[resolution]
        old = st["frontier"]
        st["frontier"] = ordinal if old is None else max(old, ordinal)
        if old is None or ordinal > old:
            st["frontier_support"] = index
        st["visits"][-1].setdefault("frontier_at", st["frontier"])
        window = cfg.get("progression_window_ms")
        visits = st["visits"]
        if window is None:
            anchor = visits[0]
        else:
            eligible_anchors = [v for v in visits if fix["end_ms"] - v["start_ms"] >= window]
            anchor = eligible_anchors[-1] if eligible_anchors else visits[0]
        indices = list(range(anchor["first_index"], index + 1))
        observed = sum(fixations[j]["duration_ms"] for j in indices)
        elapsed = fix["end_ms"] - anchor["start_ms"]
        fraction = observed / elapsed if elapsed > 0 else None
        gain = st["frontier"] - anchor["frontier_at"]
        rate = 1000 * gain / elapsed if elapsed > 0 else None
        limit = cfg.get(f"{resolution}_progression_max_units_per_s")
        slow = (rate <= limit if limit is not None and window is not None and
                elapsed >= window and fraction is not None and
                fraction >= cfg["progression_min_observed_fraction"] else None)
        metrics = dict(kind="frontier_progression", ordinal=ordinal,
                       frontier_before=old, frontier_after=st["frontier"],
                       window_baseline_frontier=anchor["frontier_at"],
                       window_start_ms=anchor["start_ms"], observed_span_ms=observed,
                       elapsed_ms=elapsed, observed_fraction=fraction,
                       frontier_gain_units=gain, units="word_ordinals" if resolution == "word" else "physical_lines",
                       frontier_gain_units_per_s=rate, slow_flag=slow)
        if resolution == "word":
            metrics["frontier_text_utf16_end"] = word_ranges[st["frontier"]][1]
        emit("P12", resolution, indices, metrics,
             ["observed_frontier_only", "elapsed_time_contains_unobserved_gaps"])

    for index, fix in enumerate(fixations):
        word = ordinals.get((fix.get("word_start"), fix.get("word_end")))
        line = fix.get("line")
        if not isinstance(line, int) or isinstance(line, bool) or line < 0:
            line = None
        for resolution, ordinal in (("word", word), ("line", line)):
            st = state[resolution]
            prev_index = st["previous"]
            if prev_index is not None:
                prev = fixations[prev_index]
                reasons = []
                if fix["segment"] != prev["segment"]:
                    reasons = [boundaries[j]["reason"] for j in range(prev["segment"], fix["segment"])
                               if j in boundaries] or ["segment_change_unknown"]
                current_geometry = fix.get("geometry_snapshot_id") or fix.get("geometry_id")
                prior_geometry = prev.get("geometry_snapshot_id") or prev.get("geometry_id")
                if current_geometry != prior_geometry and (current_geometry is not None or
                                                          prior_geometry is not None):
                    reasons.append("geometry_change")
                if fix["start_ms"] - prev["end_ms"] > max_transition_gap_ms:
                    reasons.append("unclassified_fixation_gap")
                for reason in dict.fromkeys(reasons):
                    break_path(resolution, reason, prev_index, index, fix["start_ms"])
                    st = state[resolution]
            if ordinal is None:
                if st["previous"] is not None or index == 0:
                    break_path(resolution, f"unassigned_{resolution}", st["previous"], index,
                               fix["start_ms"])
                continue
            prev_index = st["previous"]
            previous_ordinal = st["visits"][-1]["ordinal"] if st["visits"] else None
            if prev_index is not None:
                prev = fixations[prev_index]
                delta = ordinal - previous_ordinal
                if resolution == "word":
                    pid = "P01" if delta == 1 else "P02" if delta > 1 else "P03" if delta < 0 else "P04"
                    emit(pid, resolution, [prev_index, index],
                         dict(from_ordinal=previous_ordinal, to_ordinal=ordinal,
                              delta_words=delta, from_line=prev.get("line"), to_line=fix.get("line")),
                         ["intervening_text_not_observed"] if pid == "P02" else ())
                    if delta < 0 and prev.get("line") == fix.get("line") and line is not None:
                        emit("P07", resolution, [prev_index, index],
                             dict(delta_words=delta, line=line))
                elif delta != 0:
                    pid = "P05" if delta == 1 else "P06" if delta > 1 else "P08"
                    emit(pid, resolution, [prev_index, index],
                         dict(from_line=previous_ordinal, to_line=ordinal, delta_lines=delta),
                         ["intervening_text_not_observed"] if pid == "P06" else ())
                    if delta == 1:
                        end_fraction, end_reason = _line_region(prev, geometries, cfg.get('text_direction'))
                        start_fraction, start_reason = _line_region(fix, geometries, cfg.get('text_direction'))
                        reason = ("not_configured" if "return_sweep_end_fraction" not in cfg else
                                  end_reason or start_reason)
                        if reason is None and not (_number(prev.get("x")) and _number(fix.get("x"))):
                            reason = "coordinate_missing"
                        if reason is None and prev["x"] <= fix["x"]:
                            reason = "not_leftward"
                        emit("P09", resolution, [prev_index, index],
                             dict(status="abstain" if reason else "observed" if
                                  end_fraction >= cfg["return_sweep_end_fraction"] and
                                  start_fraction <= cfg["return_sweep_start_fraction"] else "not_matched",
                                  reason=reason, text_direction_requirement='ltr',
                                  configured_text_direction=cfg.get('text_direction'),
                                  direction_provenance='geometry_or_explicit_study_condition',
                                  from_end_fraction=end_fraction,
                                  to_start_fraction=start_fraction, delta_x_px=(fix["x"] - prev["x"])
                                  if _number(prev.get("x")) and _number(fix.get("x")) else None),
                             ["candidate_geometry_only", "undershoot_possible"])
            if not st["visits"] or ordinal != st["visits"][-1]["ordinal"]:
                old_frontier = st["frontier"]
                visit = dict(ordinal=ordinal, first_index=index, start_ms=fix["start_ms"],
                             frontier_before=old_frontier if old_frontier is not None else ordinal)
                st["visits"].append(visit)
                if st["episode"] is not None:
                    episode = st["episode"]
                    phase = ('frontier_reached' if ordinal >= episode['frontier'] else
                             'approaching_frontier' if previous_ordinal is not None
                             and ordinal > previous_ordinal else 'backward_within_episode')
                    support = episode['support_indices'] + list(range(episode['first_index'], index + 1))
                    emit("P10", resolution, support,
                         dict(phase=phase, previous_ordinal=previous_ordinal,
                              episode_id=episode["id"], revisited_ordinal=episode["revisit"],
                              remembered_frontier=episode["frontier"], current_ordinal=ordinal))
                    if ordinal >= episode["frontier"]:
                        st["episode"] = None
                elif ordinal in st["seen"]:
                    support = [st["seen"][ordinal], st["frontier_support"], index]
                    episode = dict(id=f"{resolution}-{st['group']}-{index}",
                                   revisit=ordinal, frontier=old_frontier, first_index=index,
                                   support_indices=support)
                    st["episode"] = episode
                    emit("P10", resolution, support, dict(phase="revisit", episode_id=episode["id"],
                         revisited_ordinal=ordinal, remembered_frontier=old_frontier))
                st["seen"].setdefault(ordinal, index)
                last = st["visits"][-4:]
                if len(last) == 4 and [v["ordinal"] for v in last] == [last[0]["ordinal"], last[1]["ordinal"], last[0]["ordinal"], last[1]["ordinal"]] and last[0]["ordinal"] != last[1]["ordinal"]:
                    span = fix["end_ms"] - last[0]["start_ms"]
                    if cfg.get("cycle_window_ms") is None or span <= cfg["cycle_window_ms"]:
                        emit("P11", resolution, [v["first_index"] for v in last],
                             dict(kind="alternation_abab", ordinals=[v["ordinal"] for v in last],
                                  elapsed_ms=span, cycle_window_ms=cfg.get("cycle_window_ms")),
                             ["structural_cycle_only"])
            st["previous"] = index
            update_residence(resolution, index, ordinal, fix)
            update_progression(resolution, index, ordinal, fix)

    # Boundary events can exist even after the last fixation. Preserve their count and time.
    last_segment = fixations[-1]["segment"] if fixations else -1
    for boundary in segment_boundaries:
        if boundary["from_segment"] >= last_segment:
            for resolution in ("word", "line"):
                event = emit("P13", resolution, [], dict(reason=boundary["reason"],
                             terminal=True, from_segment=boundary["from_segment"],
                             to_segment=boundary.get("to_segment"), at_ms=boundary.get("at_ms")))
                event["start_ms"] = event["end_ms"] = boundary.get("at_ms")
                event["evidence_available_ms"] = (boundary.get("evidence_available_ms")
                                                  if _number(boundary.get("evidence_available_ms")) else None)
                event["evidence_time_basis"] = boundary.get("evidence_time_basis")
    registry = {pid: dict(label=label, resolution=resolution, primary_sources=sources,
                          scientific_status=("operational_adaptation_not_published_threshold"
                                             if pid in ("P09", "P10", "P11", "P12", "P13")
                                             else "descriptive_operational_rule"))
                for pid, (label, resolution, sources) in REGISTRY.items()}
    pattern_status = {}
    for pid in REGISTRY:
        matched = [e for e in events if e["pattern_id"] == pid]
        if pid == "P09":
            reasons = Counter(e["metrics"].get("reason") for e in matched)
            pattern_status[pid] = dict(status="not_configured" if "return_sweep_end_fraction" not in cfg
                                       else "observed" if any(e["metrics"]["status"] == "observed" for e in matched)
                                       else "abstained" if any(e["metrics"]["status"] == "abstain" for e in matched)
                                       else "no_qualifying_event", reasons={str(k): v for k, v in reasons.items()})
        else:
            pattern_status[pid] = dict(status="observed" if matched else "no_qualifying_event")
        if pid == "P12":
            pattern_status[pid]["word_long_flag"] = ("configured" if "word_residence_ms" in cfg
                                                     else "not_configured")
            pattern_status[pid]["line_long_flag"] = ("configured" if "line_residence_ms" in cfg
                                                     else "not_configured")
            pattern_status[pid]["word_slow_flag"] = ("configured" if
                "word_progression_max_units_per_s" in cfg else "not_configured")
            pattern_status[pid]["line_slow_flag"] = ("configured" if
                "line_progression_max_units_per_s" in cfg else "not_configured")
    matched_counts = Counter(e["pattern_id"] for e in events if
                             e["pattern_id"] != "P09" or e["metrics"].get("status") == "observed")
    return dict(classifier_version=1, registry=registry, config=cfg, events=events,
                counts=dict(sorted(counts.items())),
                matched_counts=dict(sorted(matched_counts.items())),
                pattern_status=pattern_status,
                status=dict(interpretation="observed_candidate_scanpath_only",
                            difficulty_classification=False,
                            return_sweep="configured" if "return_sweep_end_fraction" in cfg else "not_configured",
                            long_slow_thresholds="explicit_only"))
