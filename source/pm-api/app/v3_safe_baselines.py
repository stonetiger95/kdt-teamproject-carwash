"""Time-safe robust baseline correction for the supervised v2 candidate.

Stage 1 reproduced the historical notebook exactly.  A later audit found that
the notebook's fallback scale for zero-MAD count sensors used all strict-normal
rows.  This module corrects that narrow issue without rewriting the historical
stage-1 artifacts: both device baselines and fallback scales are derived only
from each device's first 168 eligible hours.
"""

from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd


DEVICE_COLUMN = "equipment_id"
TIME_COLUMN = "collected_at"
CORE_FEATURES = [
    "signal_strength_dbm",
    "success_rate",
    "response_time_ms",
    "retry_count",
    "disconnect_count",
    "packet_loss_rate",
    "error_count",
    "success_rate_mean_24h",
    "response_time_ms_mean_24h",
    "packet_loss_rate_mean_24h",
    "error_count_sum_24h",
    "retry_count_sum_24h",
    "signal_strength_dbm_std_24h",
]


def recompute_time_safe_robust_features(
    data: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Recompute robust-z values from a fixed seven-day baseline window.

    The pooled fallback is needed for sparse sensors whose first-week MAD is
    zero.  It is intentionally calculated from the same first-week windows,
    so later validation months cannot affect the transformation.
    """

    missing = sorted(set(CORE_FEATURES) - set(data.columns))
    if missing:
        raise ValueError(f"core baseline features are missing: {missing}")

    frame = data.sort_values([DEVICE_COLUMN, TIME_COLUMN]).copy()
    baseline_source = frame.groupby(DEVICE_COLUMN, sort=False).head(168).copy()
    counts = baseline_source.groupby(DEVICE_COLUMN).size()
    if not counts.eq(168).all():
        raise ValueError(
            "each device requires 168 baseline rows: "
            f"{counts[counts.ne(168)].to_dict()}"
        )

    pooled_mad = baseline_source[CORE_FEATURES].apply(
        lambda values: np.median(np.abs(values - np.median(values)))
    )
    pooled_scale = (1.4826 * pooled_mad).replace(0, np.nan)
    pooled_scale = (
        pooled_scale.fillna(baseline_source[CORE_FEATURES].std())
        .replace(0, 1.0)
    )

    rows: list[dict[str, Any]] = []
    for equipment_id, group in baseline_source.groupby(
        DEVICE_COLUMN, sort=False
    ):
        row: dict[str, Any] = {
            DEVICE_COLUMN: equipment_id,
            "baseline_start": group[TIME_COLUMN].min(),
            "baseline_end": group[TIME_COLUMN].max(),
            "baseline_rows": len(group),
        }
        for feature in CORE_FEATURES:
            median = float(group[feature].median())
            mad = float(np.median(np.abs(group[feature] - median)))
            scale = 1.4826 * mad
            fallback_used = not np.isfinite(scale) or scale <= 1e-12
            if fallback_used:
                scale = float(pooled_scale[feature])
            row[f"{feature}__median"] = median
            row[f"{feature}__scale"] = scale
            row[f"{feature}__fallback_used"] = bool(fallback_used)
        rows.append(row)
    baselines = pd.DataFrame(rows)

    lookup = baselines.set_index(DEVICE_COLUMN)
    for feature in CORE_FEATURES:
        robust_name = f"{feature}__robust_z"
        frame[robust_name] = (
            frame[feature]
            - frame[DEVICE_COLUMN].map(lookup[f"{feature}__median"])
        ) / frame[DEVICE_COLUMN].map(lookup[f"{feature}__scale"])

    robust_names = [f"{feature}__robust_z" for feature in CORE_FEATURES]
    if not np.isfinite(frame[robust_names]).all().all():
        raise AssertionError("time-safe robust features contain non-finite values")
    return frame.sort_index(), baselines

