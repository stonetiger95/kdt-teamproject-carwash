from __future__ import annotations

import numpy as np
import pandas as pd

from app.v3_model_training import (
    TARGET_COLUMN,
    apply_calibrator,
    fit_calibrator,
    select_alert_thresholds,
    select_feature_group,
    threshold_metrics,
)
from app.v3_safe_baselines import (
    CORE_FEATURES,
    recompute_time_safe_robust_features,
)


def _baseline_frame(periods: int = 220) -> pd.DataFrame:
    rows = []
    for device_index, device in enumerate(["ANT-A", "ANT-B"]):
        index = np.arange(periods, dtype=float)
        frame = pd.DataFrame(
            {
                "equipment_id": device,
                "collected_at": pd.date_range(
                    "2026-01-01", periods=periods, freq="h"
                ),
            }
        )
        for feature_index, feature in enumerate(CORE_FEATURES):
            frame[feature] = (
                index * (feature_index + 1) / 100
                + device_index
                + feature_index
            )
        rows.append(frame)
    return pd.concat(rows, ignore_index=True)


def test_time_safe_baseline_ignores_later_value_changes():
    original = _baseline_frame()
    modified = original.copy()
    modified.loc[modified.groupby("equipment_id").cumcount().ge(168), CORE_FEATURES] += 1_000

    original_features, original_baselines = recompute_time_safe_robust_features(
        original
    )
    modified_features, modified_baselines = recompute_time_safe_robust_features(
        modified
    )

    pd.testing.assert_frame_equal(original_baselines, modified_baselines)
    robust = [f"{feature}__robust_z" for feature in CORE_FEATURES]
    pd.testing.assert_frame_equal(
        original_features.groupby("equipment_id").head(168)[robust],
        modified_features.groupby("equipment_id").head(168)[robust],
    )


def test_platt_calibrator_returns_bounded_probabilities():
    raw = np.array([0.02, 0.10, 0.30, 0.70, 0.90, 0.98])
    target = np.array([0, 0, 0, 1, 1, 1])
    calibrator = fit_calibrator("platt", raw, target)
    calibrated = apply_calibrator("platt", calibrator, raw)

    assert np.all((calibrated >= 0) & (calibrated <= 1))
    assert np.all(np.diff(calibrated) >= 0)


def test_feature_selection_prefers_simpler_group_within_tolerance():
    comparison = pd.DataFrame(
        [
            {
                "feature_group": "snapshot_13",
                "feature_count": 13,
                "calibration": "uncalibrated",
                "pooled_oof_pr_auc": 0.40,
            },
            {
                "feature_group": "full_71",
                "feature_count": 71,
                "calibration": "uncalibrated",
                "pooled_oof_pr_auc": 0.42,
            },
        ]
    )
    temporal = pd.DataFrame(
        [
            {
                "feature_group": "snapshot_13",
                "calibration": "uncalibrated",
                "pr_auc": 0.40,
            },
            {
                "feature_group": "full_71",
                "calibration": "uncalibrated",
                "pr_auc": 0.41,
            },
        ]
    )

    selected, decision = select_feature_group(comparison, temporal)

    assert selected == "snapshot_13"
    assert decision.loc[
        decision["feature_group"].eq("snapshot_13"), "within_tolerance"
    ].item()


def test_thresholds_are_ordered_and_event_metrics_are_counted():
    times = pd.date_range("2026-01-01", periods=240, freq="h")
    probability = np.linspace(0.01, 0.99, len(times))
    target = (times >= pd.Timestamp("2026-01-04")).astype(int)
    scored = pd.DataFrame(
        {
            "equipment_id": "ANT-A",
            "collected_at": times,
            TARGET_COLUMN: target,
            "probability": probability,
        }
    )
    events = pd.DataFrame(
        {
            "equipment_id": ["ANT-A"],
            "failure_time": [pd.Timestamp("2026-01-09")],
        }
    )

    thresholds, _ = select_alert_thresholds(scored, events)
    metrics = threshold_metrics(scored, events, thresholds["warning"])

    assert thresholds["critical"] >= thresholds["warning"]
    assert metrics["failure_events"] == 1
    assert metrics["detected_events"] == 1

