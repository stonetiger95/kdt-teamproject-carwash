from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from app.v3_preprocessing import (
    DEVICE_COLUMN,
    LEAKAGE_OR_METADATA_COLUMNS,
    TARGET_COLUMN,
    TIME_COLUMN,
    build_device_fold_assignment,
    build_online_supervised_row,
    build_supervised_features,
    validate_and_sort_raw_data,
)


def make_modeling_rows(
    equipment_id: str,
    *,
    periods: int = 48,
    start: str = "2026-01-01",
) -> pd.DataFrame:
    index = np.arange(periods, dtype=float)
    return pd.DataFrame(
        {
            DEVICE_COLUMN: equipment_id,
            TIME_COLUMN: pd.date_range(start, periods=periods, freq="h"),
            TARGET_COLUMN: np.zeros(periods, dtype=int),
            "traffic_load": 0.2 + index / 1000,
            "temperature_c": 20 + index / 100,
            "voltage_v": 12 + index / 1000,
            "signal_strength_dbm": 75 - index / 100,
            "success_rate": 99 - index / 100,
            "response_time_ms": 50 + index,
            "retry_count": (index % 4).astype(int),
            "disconnect_count": (index % 2).astype(int),
            "packet_loss_rate": index / 100,
            "error_count": (index % 3).astype(int),
            "days_since_maintenance": 30 + index.astype(int) // 24,
        }
    )


def make_raw_rows() -> pd.DataFrame:
    frame = make_modeling_rows("ANT-TEST", periods=4)
    frame["location_id"] = "TG-01"
    frame["equipment_type"] = "antenna"
    frame["failure_event"] = 0
    frame["maintenance_event"] = 0
    return frame


def test_future_changes_do_not_change_past_features():
    original = make_modeling_rows("ANT-TEST", periods=48)
    modified = original.copy()
    modified.loc[modified.index >= 30, "response_time_ms"] = 99_999

    original_features, feature_names = build_supervised_features(original)
    modified_features, _ = build_supervised_features(modified)

    pd.testing.assert_series_equal(
        original_features.loc[24, feature_names],
        modified_features.loc[24, feature_names],
        check_names=False,
    )


def test_time_gap_resets_delta_and_rolling_window():
    frame = make_modeling_rows("ANT-TEST", periods=30)
    frame.loc[24:, TIME_COLUMN] += pd.Timedelta(hours=3)

    features, _ = build_supervised_features(frame)
    first_after_gap = features.iloc[24]

    assert np.isnan(first_after_gap["response_time_ms_delta_1h"])
    assert first_after_gap["response_time_ms_mean_6h"] == pytest.approx(
        frame.iloc[24]["response_time_ms"]
    )


def test_online_reference_matches_batch_last_row():
    frame = make_modeling_rows("ANT-TEST", periods=48)
    batch, feature_names = build_supervised_features(frame)
    online = build_online_supervised_row(frame)

    pd.testing.assert_series_equal(
        batch.iloc[-1][feature_names],
        online,
        check_names=False,
    )


def test_supervised_manifest_never_contains_leakage_columns():
    frame = make_modeling_rows("ANT-TEST", periods=48)
    _, feature_names = build_supervised_features(frame)

    assert len(feature_names) == 71
    assert not set(feature_names) & LEAKAGE_OR_METADATA_COLUMNS


def test_device_fold_assignment_is_reproducible_and_balanced():
    frames = []
    for index in range(24):
        item = make_modeling_rows(f"ANT-{index + 1:03d}", periods=2)
        if index % 2 == 0:
            item.loc[item.index[-1], TARGET_COLUMN] = 1
        frames.append(item)
    data = pd.concat(frames, ignore_index=True)

    first = build_device_fold_assignment(data)
    second = build_device_fold_assignment(data)

    pd.testing.assert_frame_equal(first, second)
    balance = first.groupby("outer_fold")["is_failure_device"].agg(
        ["sum", "count"]
    )
    assert balance["sum"].between(2, 3).all()
    assert (balance["count"] - balance["sum"]).between(2, 3).all()


def test_raw_validation_rejects_duplicate_device_timestamp():
    frame = make_raw_rows()
    duplicate = pd.concat([frame, frame.iloc[[0]]], ignore_index=True)

    with pytest.raises(ValueError, match="duplicate"):
        validate_and_sort_raw_data(duplicate)
