"""Reproducible, leakage-safe preprocessing for predictive-maintenance v3 data.

The original v3 experiments were developed in notebooks.  This module turns the
approved parts of those notebooks into reusable functions so that training and
online inference can share one feature contract.

Important boundaries:

* Labels and future-derived metadata are retained only for audit/evaluation.
* Every rolling feature uses the current row and rows that occurred before it.
* A one-hour gap starts a new segment, so rolling windows never cross downtime,
  maintenance, or missing-data gaps.
* This module does not replace the currently deployed ``logistic-platt-v1``.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd


SCHEMA_VERSION = "v3-preprocessing-1.0.0"
DEVICE_COLUMN = "equipment_id"
TIME_COLUMN = "collected_at"
TARGET_COLUMN = "failure_within_7d"

RAW_SENSOR_FEATURES = [
    "traffic_load",
    "temperature_c",
    "voltage_v",
    "signal_strength_dbm",
    "success_rate",
    "response_time_ms",
    "retry_count",
    "disconnect_count",
    "packet_loss_rate",
    "error_count",
    "days_since_maintenance",
]

# retry_count and error_count receive count-specific rolling features.  The
# maintenance age is cumulative already, so none of the three are duplicated
# in the generic rolling family.
ROLLING_SOURCE_FEATURES = [
    "traffic_load",
    "temperature_c",
    "voltage_v",
    "signal_strength_dbm",
    "success_rate",
    "response_time_ms",
    "disconnect_count",
    "packet_loss_rate",
]

COUNT_SOURCE_FEATURES = ["error_count", "retry_count"]
COUNT_SUFFIXES = [
    "sum_6h",
    "sum_24h",
    "sum_168h",
    "nonzero_rate_24h",
    "ge3_rate_24h",
    "max_24h",
]

CORE_RAW_FEATURES = [
    "signal_strength_dbm",
    "success_rate",
    "response_time_ms",
    "retry_count",
    "disconnect_count",
    "packet_loss_rate",
    "error_count",
]
CORE_ROLLING_FEATURES = [
    "success_rate_mean_24h",
    "response_time_ms_mean_24h",
    "packet_loss_rate_mean_24h",
    "error_count_sum_24h",
    "retry_count_sum_24h",
    "signal_strength_dbm_std_24h",
]
CORE_FEATURES = CORE_RAW_FEATURES + CORE_ROLLING_FEATURES

LEAKAGE_OR_METADATA_COLUMNS = {
    TARGET_COLUMN,
    "failure_event",
    "failure_start",
    "failure_case_id",
    "failure_start_time",
    "failure_time",
    "failure_last_record_time",
    "first_failure_time",
    "hours_until_failure",
    "expected_failure_within_7d",
    "rul_hours",
    "risk_score",
    "risk_level",
    "after_failure_before_maintenance",
    "maintenance_event",
    "day_index",
    "time_diff",
    "equipment_type",
}

REQUIRED_COLUMNS = {
    DEVICE_COLUMN,
    "location_id",
    "equipment_type",
    TIME_COLUMN,
    *RAW_SENSOR_FEATURES,
    "failure_event",
    "maintenance_event",
    TARGET_COLUMN,
}

NUMERIC_COLUMNS = [
    *RAW_SENSOR_FEATURES,
    "failure_event",
    "maintenance_event",
    TARGET_COLUMN,
]

EXPECTED_V3_PROFILE = {
    "raw_rows": 69_120,
    "modeling_rows": 68_734,
    "devices": 24,
    "failure_events": 12,
    "failure_rows": 36,
    "maintenance_rows": 12,
    "positive_rows": 2_004,
}


@dataclass(frozen=True)
class V3PreprocessingResult:
    """All deterministic outputs required by the first migration stage."""

    modeling_base: pd.DataFrame
    failure_events: pd.DataFrame
    supervised_features: pd.DataFrame
    normal_only_features: pd.DataFrame
    device_baselines: pd.DataFrame
    fold_assignment: pd.DataFrame
    feature_manifest: dict[str, Any]
    quality_report: dict[str, Any]


def load_v3_csv(path: str | Path) -> pd.DataFrame:
    """Load and validate the canonical v3 CSV.

    UTF-8 with BOM is preferred, while CP949 remains a compatibility fallback
    for files exported from Korean spreadsheet tools.
    """

    source = Path(path)
    if not source.exists():
        raise FileNotFoundError(f"v3 data file does not exist: {source}")

    try:
        frame = pd.read_csv(source, encoding="utf-8-sig")
    except UnicodeDecodeError:
        frame = pd.read_csv(source, encoding="cp949")

    return validate_and_sort_raw_data(frame)


def validate_and_sort_raw_data(data: pd.DataFrame) -> pd.DataFrame:
    """Validate schema, types, key uniqueness, and sensor domains."""

    missing_columns = sorted(REQUIRED_COLUMNS - set(data.columns))
    if missing_columns:
        raise ValueError(f"required v3 columns are missing: {missing_columns}")

    frame = data.copy()
    frame[TIME_COLUMN] = pd.to_datetime(frame[TIME_COLUMN], errors="raise")
    if frame[TIME_COLUMN].dt.tz is not None:
        frame[TIME_COLUMN] = (
            frame[TIME_COLUMN]
            .dt.tz_convert("Asia/Seoul")
            .dt.tz_localize(None)
        )

    if frame[[DEVICE_COLUMN, TIME_COLUMN]].isna().any().any():
        raise ValueError("equipment_id and collected_at must not be null")

    for column in NUMERIC_COLUMNS:
        frame[column] = pd.to_numeric(frame[column], errors="raise")

    sensor_missing = frame[RAW_SENSOR_FEATURES].isna().sum()
    sensor_missing = sensor_missing[sensor_missing.gt(0)]
    if not sensor_missing.empty:
        raise ValueError(
            "raw sensor columns contain missing values: "
            f"{sensor_missing.to_dict()}"
        )

    for column in ["failure_event", "maintenance_event", TARGET_COLUMN]:
        invalid = ~frame[column].isin([0, 1])
        if invalid.any():
            raise ValueError(f"{column} must contain only 0 or 1")

    percentage_ranges = {
        "success_rate": (0.0, 100.0),
        "packet_loss_rate": (0.0, 100.0),
    }
    for column, (minimum, maximum) in percentage_ranges.items():
        if not frame[column].between(minimum, maximum).all():
            raise ValueError(
                f"{column} must be between {minimum} and {maximum}"
            )

    for column in [
        "retry_count",
        "disconnect_count",
        "error_count",
        "days_since_maintenance",
    ]:
        if frame[column].lt(0).any():
            raise ValueError(f"{column} must not be negative")

    if frame.duplicated([DEVICE_COLUMN, TIME_COLUMN]).any():
        raise ValueError("duplicate equipment_id/collected_at keys detected")

    return (
        frame.sort_values([DEVICE_COLUMN, TIME_COLUMN])
        .reset_index(drop=True)
    )


def prepare_modeling_base(
    raw_data: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Create the leakage-audited modeling rows and independent failure events.

    The v3 generator records one physical failure in three consecutive hourly
    rows.  The midpoint is the reference failure time.  Rows from the start of
    the recorded failure until maintenance are removed because they describe an
    already-failed machine rather than a future failure warning.
    """

    frame = validate_and_sort_raw_data(raw_data)
    frame["time_diff"] = frame.groupby(DEVICE_COLUMN)[TIME_COLUMN].diff()
    previous_failure = frame.groupby(DEVICE_COLUMN)["failure_event"].shift(
        fill_value=0
    )
    frame["failure_start"] = (
        frame["failure_event"].eq(1)
        & (
            previous_failure.ne(1)
            | frame["time_diff"].gt(pd.Timedelta(hours=1))
        )
    )
    frame["failure_case_id"] = (
        frame.groupby(DEVICE_COLUMN)["failure_start"]
        .cumsum()
        .astype(int)
    )

    failure_rows = frame.loc[frame["failure_event"].eq(1)].copy()
    failure_events = (
        failure_rows.groupby(
            [DEVICE_COLUMN, "failure_case_id"],
            as_index=False,
        )
        .agg(
            failure_start_time=(TIME_COLUMN, "min"),
            failure_last_record_time=(TIME_COLUMN, "max"),
            recorded_rows=(TIME_COLUMN, "size"),
        )
    )
    failure_events["failure_time"] = (
        failure_events["failure_start_time"]
        + (
            failure_events["failure_last_record_time"]
            - failure_events["failure_start_time"]
        )
        / 2
    )

    first_failure_time = (
        failure_events.groupby(DEVICE_COLUMN)["failure_time"]
        .min()
        .to_dict()
    )
    frame["first_failure_time"] = frame[DEVICE_COLUMN].map(
        first_failure_time
    )
    frame["hours_until_failure"] = (
        frame["first_failure_time"] - frame[TIME_COLUMN]
    ).dt.total_seconds().div(3600)
    frame["expected_failure_within_7d"] = (
        frame["hours_until_failure"].between(0, 168).fillna(False).astype(int)
    )

    label_mismatches = int(
        frame[TARGET_COLUMN]
        .astype(int)
        .ne(frame["expected_failure_within_7d"])
        .sum()
    )
    if label_mismatches:
        raise ValueError(
            "failure_within_7d does not match the failure timestamps: "
            f"{label_mismatches} rows"
        )

    # The explicit state loop is intentional: it mirrors the business event
    # order and is easier to audit than a compact cumulative-sum expression.
    frame["after_failure_before_maintenance"] = False
    for _, indices in frame.groupby(DEVICE_COLUMN, sort=False).groups.items():
        in_downtime = False
        for row_index in indices:
            if frame.at[row_index, "maintenance_event"] == 1:
                in_downtime = False
                continue
            if bool(frame.at[row_index, "failure_start"]):
                in_downtime = True
            if in_downtime:
                frame.at[
                    row_index,
                    "after_failure_before_maintenance",
                ] = True

    modeling_base = frame.loc[
        ~frame["after_failure_before_maintenance"]
        & frame["maintenance_event"].eq(0)
    ].copy()
    modeling_base = modeling_base.reset_index(drop=True)

    if (
        modeling_base["after_failure_before_maintenance"].any()
        or modeling_base["maintenance_event"].ne(0).any()
    ):
        raise AssertionError("failure/maintenance rows leaked into modeling data")

    return modeling_base, failure_events


def add_continuous_segments(data: pd.DataFrame) -> pd.DataFrame:
    """Assign a new segment whenever observations are not exactly one hour apart."""

    frame = (
        data.sort_values([DEVICE_COLUMN, TIME_COLUMN])
        .reset_index(drop=True)
        .copy()
    )
    new_segment = (
        frame.groupby(DEVICE_COLUMN)[TIME_COLUMN]
        .diff()
        .ne(pd.Timedelta(hours=1))
    )
    frame["_segment_id"] = (
        new_segment.groupby(frame[DEVICE_COLUMN])
        .cumsum()
        .astype(int)
    )
    frame["_segment_position"] = frame.groupby(
        [DEVICE_COLUMN, "_segment_id"],
        sort=False,
    ).cumcount()
    return frame


def build_supervised_features(
    modeling_base: pd.DataFrame,
) -> tuple[pd.DataFrame, list[str]]:
    """Build the 71-feature causal v3 supervised-learning matrix."""

    frame = add_continuous_segments(modeling_base)
    rolling_keys = [DEVICE_COLUMN, "_segment_id"]
    rolling_features: list[str] = []

    for feature in ROLLING_SOURCE_FEATURES:
        grouped = frame.groupby(rolling_keys, sort=False)[feature]

        delta_name = f"{feature}_delta_1h"
        frame[delta_name] = grouped.diff(1)
        rolling_features.append(delta_name)

        for window in [6, 24, 168]:
            mean_name = f"{feature}_mean_{window}h"
            frame[mean_name] = grouped.transform(
                lambda values, window=window: values.rolling(
                    window,
                    min_periods=1,
                ).mean()
            )
            rolling_features.append(mean_name)

        for window in [24, 168]:
            std_name = f"{feature}_std_{window}h"
            frame[std_name] = grouped.transform(
                lambda values, window=window: values.rolling(
                    window,
                    min_periods=2,
                ).std()
            )
            rolling_features.append(std_name)

    count_features: list[str] = []
    for feature in COUNT_SOURCE_FEATURES:
        grouped = frame.groupby(rolling_keys, sort=False)[feature]

        for window in [6, 24, 168]:
            name = f"{feature}_sum_{window}h"
            frame[name] = grouped.transform(
                lambda values, window=window: values.rolling(
                    window,
                    min_periods=1,
                ).sum()
            )
            count_features.append(name)

        nonzero_name = f"{feature}_nonzero_rate_24h"
        frame[nonzero_name] = grouped.transform(
            lambda values: values.gt(0).astype(float).rolling(
                24,
                min_periods=1,
            ).mean()
        )
        count_features.append(nonzero_name)

        ge3_name = f"{feature}_ge3_rate_24h"
        frame[ge3_name] = grouped.transform(
            lambda values: values.ge(3).astype(float).rolling(
                24,
                min_periods=1,
            ).mean()
        )
        count_features.append(ge3_name)

        max_name = f"{feature}_max_24h"
        frame[max_name] = grouped.transform(
            lambda values: values.rolling(24, min_periods=1).max()
        )
        count_features.append(max_name)

    feature_names = sorted(
        set(RAW_SENSOR_FEATURES + rolling_features + count_features)
    )
    if len(feature_names) != 71:
        raise AssertionError(
            f"expected 71 supervised features, got {len(feature_names)}"
        )

    leaked = sorted(set(feature_names) & LEAKAGE_OR_METADATA_COLUMNS)
    if leaked:
        raise AssertionError(f"leakage columns included in features: {leaked}")

    first_rows = frame.groupby(rolling_keys, sort=False).head(1)
    for feature in ROLLING_SOURCE_FEATURES:
        if not first_rows[f"{feature}_delta_1h"].isna().all():
            raise AssertionError("delta feature crossed a segment boundary")
        if not np.allclose(
            first_rows[f"{feature}_mean_6h"],
            first_rows[feature],
        ):
            raise AssertionError("rolling mean crossed a segment boundary")

    columns = [
        DEVICE_COLUMN,
        TIME_COLUMN,
        TARGET_COLUMN,
        "_segment_id",
        *feature_names,
    ]
    return frame[columns].copy(), feature_names


def build_normal_only_features(
    modeling_base: pd.DataFrame,
    failure_events: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.DataFrame, list[str]]:
    """Build 13 robust device-normalized features for the OCSVM companion."""

    frame = add_continuous_segments(modeling_base)
    keys = [DEVICE_COLUMN, "_segment_id"]

    rolling_definitions = {
        "success_rate_mean_24h": ("success_rate", "mean"),
        "response_time_ms_mean_24h": ("response_time_ms", "mean"),
        "packet_loss_rate_mean_24h": ("packet_loss_rate", "mean"),
        "error_count_sum_24h": ("error_count", "sum"),
        "retry_count_sum_24h": ("retry_count", "sum"),
        "signal_strength_dbm_std_24h": ("signal_strength_dbm", "std"),
    }
    for output_name, (source_name, operation) in rolling_definitions.items():
        grouped = frame.groupby(keys, sort=False)[source_name]
        frame[output_name] = grouped.transform(
            lambda values, operation=operation: getattr(
                values.rolling(24, min_periods=24),
                operation,
            )()
        )

    frame["warmup_complete"] = frame["_segment_position"].ge(23)
    frame["model_eligible"] = (
        frame["warmup_complete"]
        & frame[CORE_FEATURES].notna().all(axis=1)
        & np.isfinite(frame[CORE_FEATURES]).all(axis=1)
    )

    first_failure_time = (
        failure_events.groupby(DEVICE_COLUMN)["failure_time"]
        .min()
        .to_dict()
    )
    frame["failure_start_time"] = frame[DEVICE_COLUMN].map(
        first_failure_time
    )
    frame["is_failure_device"] = frame[DEVICE_COLUMN].isin(
        first_failure_time
    )
    frame["hours_to_failure"] = (
        frame["failure_start_time"] - frame[TIME_COLUMN]
    ).dt.total_seconds().div(3600)

    conditions = [
        ~frame["model_eligible"],
        ~frame["is_failure_device"],
        frame["hours_to_failure"].gt(24 * 30),
        frame["hours_to_failure"].ge(24 * 7),
        frame["hours_to_failure"].gt(0),
    ]
    roles = [
        "warmup_excluded",
        "strict_normal",
        "expanded_normal",
        "boundary_excluded",
        "failure_evaluation",
    ]
    frame["data_role"] = np.select(
        conditions,
        roles,
        default="post_failure_excluded",
    )

    eligible = frame.loc[frame["model_eligible"]].copy()
    strict_normal = eligible.loc[
        eligible["data_role"].eq("strict_normal")
    ]
    if strict_normal.empty:
        raise ValueError("strict-normal rows are required for robust baselines")

    global_median = strict_normal[CORE_FEATURES].median()
    global_mad = strict_normal[CORE_FEATURES].apply(
        lambda values: np.median(np.abs(values - np.median(values)))
    )
    global_scale = (1.4826 * global_mad).replace(0, np.nan)
    global_scale = (
        global_scale.fillna(strict_normal[CORE_FEATURES].std())
        .replace(0, 1.0)
    )

    baseline_source = (
        eligible.sort_values([DEVICE_COLUMN, TIME_COLUMN])
        .groupby(DEVICE_COLUMN, sort=False)
        .head(168)
        .copy()
    )
    baseline_rows: list[dict[str, Any]] = []
    for equipment_id, group in baseline_source.groupby(
        DEVICE_COLUMN,
        sort=False,
    ):
        row: dict[str, Any] = {
            DEVICE_COLUMN: equipment_id,
            "baseline_start": group[TIME_COLUMN].min(),
            "baseline_end": group[TIME_COLUMN].max(),
            "baseline_rows": len(group),
        }
        for feature in CORE_FEATURES:
            median = group[feature].median()
            mad = np.median(np.abs(group[feature] - median))
            scale = 1.4826 * mad
            if not np.isfinite(scale) or scale <= 1e-12:
                scale = float(global_scale[feature])
            row[f"{feature}__median"] = float(median)
            row[f"{feature}__scale"] = float(scale)
        baseline_rows.append(row)

    baselines = pd.DataFrame(baseline_rows)
    baseline_counts = baselines.set_index(DEVICE_COLUMN)["baseline_rows"]
    if not baseline_counts.eq(168).all():
        invalid = baseline_counts[~baseline_counts.eq(168)].to_dict()
        raise ValueError(
            "every device requires 168 eligible baseline rows: "
            f"{invalid}"
        )

    lookup = baselines.set_index(DEVICE_COLUMN)
    robust_features: list[str] = []
    for feature in CORE_FEATURES:
        output_name = f"{feature}__robust_z"
        eligible[output_name] = (
            eligible[feature]
            - eligible[DEVICE_COLUMN].map(lookup[f"{feature}__median"])
        ) / eligible[DEVICE_COLUMN].map(lookup[f"{feature}__scale"])
        robust_features.append(output_name)

    if not np.isfinite(eligible[robust_features]).all().all():
        raise AssertionError("non-finite robust features were generated")

    columns = [
        DEVICE_COLUMN,
        TIME_COLUMN,
        TARGET_COLUMN,
        "_segment_id",
        "data_role",
        *CORE_FEATURES,
        *robust_features,
    ]
    return eligible[columns].copy(), baselines, robust_features


def build_device_fold_assignment(
    modeling_base: pd.DataFrame,
    *,
    folds: int = 5,
    random_state: int = 42,
) -> pd.DataFrame:
    """Reproduce the stratified device-level split used by the v3 notebooks."""

    device_target = (
        modeling_base.groupby(DEVICE_COLUMN)[TARGET_COLUMN]
        .max()
        .astype(int)
    )
    failure_devices = sorted(device_target[device_target.eq(1)].index)
    normal_devices = sorted(device_target[device_target.eq(0)].index)

    rng = np.random.default_rng(random_state)
    rng.shuffle(failure_devices)
    rng.shuffle(normal_devices)

    assignment: dict[str, int] = {}
    for index, equipment_id in enumerate(failure_devices):
        assignment[equipment_id] = index % folds
    for index, equipment_id in enumerate(normal_devices):
        assignment[equipment_id] = index % folds

    result = pd.DataFrame(
        {
            DEVICE_COLUMN: sorted(assignment),
            "outer_fold": [
                assignment[equipment_id]
                for equipment_id in sorted(assignment)
            ],
            "is_failure_device": [
                int(device_target[equipment_id])
                for equipment_id in sorted(assignment)
            ],
        }
    )

    fold_balance = result.groupby("outer_fold")["is_failure_device"].agg(
        ["sum", "count"]
    )
    normal_count = fold_balance["count"] - fold_balance["sum"]
    if not fold_balance["sum"].between(2, 3).all():
        raise AssertionError("each fold must contain 2-3 failure devices")
    if not normal_count.between(2, 3).all():
        raise AssertionError("each fold must contain 2-3 normal devices")

    return result


def build_online_supervised_row(history: pd.DataFrame) -> pd.Series:
    """Reference online transform for parity tests and the future v2 API.

    ``history`` must contain one device and end at the requested prediction
    timestamp.  Reusing the batch builder here favors correctness; a cached
    implementation can replace it later after parity tests are retained.
    """

    if history[DEVICE_COLUMN].nunique() != 1:
        raise ValueError("online history must contain exactly one device")
    features, feature_names = build_supervised_features(history)
    return features.iloc[-1][feature_names]


def assert_expected_v3_profile(
    raw_data: pd.DataFrame,
    modeling_base: pd.DataFrame,
    failure_events: pd.DataFrame,
) -> None:
    """Fail fast if the canonical v3 source differs from the approved profile."""

    actual = {
        "raw_rows": len(raw_data),
        "modeling_rows": len(modeling_base),
        "devices": modeling_base[DEVICE_COLUMN].nunique(),
        "failure_events": len(failure_events),
        "failure_rows": int(raw_data["failure_event"].sum()),
        "maintenance_rows": int(raw_data["maintenance_event"].sum()),
        "positive_rows": int(modeling_base[TARGET_COLUMN].sum()),
    }
    mismatches = {
        key: {"expected": EXPECTED_V3_PROFILE[key], "actual": value}
        for key, value in actual.items()
        if value != EXPECTED_V3_PROFILE[key]
    }
    if mismatches:
        raise ValueError(f"canonical v3 profile mismatch: {mismatches}")


def run_v3_preprocessing(
    raw_data: pd.DataFrame,
    *,
    enforce_expected_profile: bool = True,
) -> V3PreprocessingResult:
    """Run the complete stage-1 preprocessing contract in memory."""

    raw = validate_and_sort_raw_data(raw_data)
    modeling_base, failure_events = prepare_modeling_base(raw)
    if enforce_expected_profile:
        assert_expected_v3_profile(raw, modeling_base, failure_events)

    supervised, supervised_names = build_supervised_features(modeling_base)
    normal_only, baselines, robust_names = build_normal_only_features(
        modeling_base,
        failure_events,
    )
    fold_assignment = build_device_fold_assignment(modeling_base)

    feature_manifest: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "timezone_assumption": "Asia/Seoul",
        "device_column": DEVICE_COLUMN,
        "time_column": TIME_COLUMN,
        "target_column": TARGET_COLUMN,
        "supervised_feature_group": "raw_plus_rolling_count",
        "supervised_feature_count": len(supervised_names),
        "supervised_features": supervised_names,
        "normal_only_raw_features": CORE_RAW_FEATURES,
        "normal_only_rolling_features": CORE_ROLLING_FEATURES,
        "normal_only_robust_features": robust_names,
        "rolling_windows_hours": [6, 24, 168],
        "normal_only_warmup_hours": 24,
        "device_baseline_eligible_rows": 168,
        "segment_rule": "new segment unless adjacent observations are exactly 1 hour apart",
        "rolling_includes_current_row": True,
        "uses_future_values": False,
        "leakage_columns": sorted(LEAKAGE_OR_METADATA_COLUMNS),
    }

    role_counts = {
        str(key): int(value)
        for key, value in normal_only["data_role"].value_counts().items()
    }
    quality_report: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "raw_rows": len(raw),
        "modeling_rows": len(modeling_base),
        "removed_rows": len(raw) - len(modeling_base),
        "device_count": int(modeling_base[DEVICE_COLUMN].nunique()),
        "failure_event_rows": int(raw["failure_event"].sum()),
        "independent_failure_events": len(failure_events),
        "maintenance_event_rows": int(raw["maintenance_event"].sum()),
        "positive_modeling_rows": int(modeling_base[TARGET_COLUMN].sum()),
        "duplicate_key_rows": int(
            raw.duplicated([DEVICE_COLUMN, TIME_COLUMN]).sum()
        ),
        "observation_start": raw[TIME_COLUMN].min().isoformat(),
        "observation_end": raw[TIME_COLUMN].max().isoformat(),
        "continuous_segments": int(
            supervised.groupby([DEVICE_COLUMN, "_segment_id"]).ngroups
        ),
        "supervised_feature_count": len(supervised_names),
        "normal_only_feature_count": len(robust_names),
        "normal_only_eligible_rows": len(normal_only),
        "normal_only_role_counts": role_counts,
        "device_baseline_count": len(baselines),
        "expected_profile_enforced": enforce_expected_profile,
        "validation_status": "passed",
    }

    return V3PreprocessingResult(
        modeling_base=modeling_base,
        failure_events=failure_events,
        supervised_features=supervised,
        normal_only_features=normal_only,
        device_baselines=baselines,
        fold_assignment=fold_assignment,
        feature_manifest=feature_manifest,
        quality_report=quality_report,
    )
