"""Leakage-safe training utilities for the v3 supervised PM candidate.

The functions in this module deliberately separate three decisions:

1. compare feature groups on identical rows and device folds;
2. choose probability calibration without looking at the April holdout;
3. freeze alert thresholds before evaluating the April holdout.

The exported artifacts are candidates only.  Production activation remains a
separate, explicit review and shadow-deployment decision.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

import json

import numpy as np
import pandas as pd
from sklearn.calibration import calibration_curve
from sklearn.impute import SimpleImputer
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    brier_score_loss,
    fbeta_score,
    log_loss,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler


DEVICE_COLUMN = "equipment_id"
TIME_COLUMN = "collected_at"
TARGET_COLUMN = "failure_within_7d"
KEY_COLUMNS = [DEVICE_COLUMN, TIME_COLUMN, TARGET_COLUMN]
COMPARISON_ROLES = {
    "strict_normal",
    "expanded_normal",
    "boundary_excluded",
    "failure_evaluation",
}
CALIBRATION_METHODS = ("uncalibrated", "platt", "isotonic")
DEFAULT_C_GRID = (0.001, 0.01, 0.1, 1.0)


@dataclass(frozen=True)
class ComparisonInputs:
    """Aligned input table plus its immutable feature and fold contracts."""

    data: pd.DataFrame
    feature_groups: dict[str, list[str]]
    fold_assignment: pd.DataFrame
    failure_events: pd.DataFrame
    feature_manifest: dict[str, Any]


def make_logistic_pipeline(c_value: float) -> Pipeline:
    """Return the reproducible L2 logistic baseline used in every comparison."""

    return Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
            (
                "classifier",
                LogisticRegression(
                    penalty="l2",
                    C=float(c_value),
                    class_weight="balanced",
                    solver="liblinear",
                    max_iter=5_000,
                    random_state=42,
                ),
            ),
        ]
    )


def load_comparison_inputs(preprocessing_dir: str | Path) -> ComparisonInputs:
    """Load and align the 13- and 71-feature tables on exactly the same rows."""

    source = Path(preprocessing_dir)
    manifest = json.loads(
        (source / "feature_manifest.json").read_text(encoding="utf-8")
    )
    full = pd.read_parquet(source / "v3_features_71.parquet")
    robust = pd.read_parquet(source / "v3_features_13_robust.parquet")
    folds = pd.read_csv(source / "device_fold_assignment.csv")
    events = pd.read_parquet(source / "failure_events.parquet")

    for frame in (full, robust):
        frame[TIME_COLUMN] = pd.to_datetime(frame[TIME_COLUMN])
    events["failure_time"] = pd.to_datetime(events["failure_time"])

    robust = robust.loc[robust["data_role"].isin(COMPARISON_ROLES)].copy()
    robust_features = manifest["normal_only_robust_features"]
    full_features = manifest["supervised_features"]
    aligned = robust[KEY_COLUMNS + ["data_role", *robust_features]].merge(
        full[KEY_COLUMNS + full_features],
        on=KEY_COLUMNS,
        how="inner",
        validate="one_to_one",
    )
    aligned = aligned.sort_values([DEVICE_COLUMN, TIME_COLUMN]).reset_index(
        drop=True
    )

    if len(aligned) != len(robust):
        raise ValueError("13/71 feature tables did not align one-to-one")
    if aligned.duplicated([DEVICE_COLUMN, TIME_COLUMN]).any():
        raise ValueError("duplicate device/timestamp keys in comparison data")
    if set(aligned[DEVICE_COLUMN]) != set(folds[DEVICE_COLUMN]):
        raise ValueError("device fold assignment does not cover comparison data")

    return ComparisonInputs(
        data=aligned,
        feature_groups={
            "snapshot_13": list(robust_features),
            "full_71": list(full_features),
        },
        fold_assignment=folds,
        failure_events=events,
        feature_manifest=manifest,
    )


def _device_fold_map(
    data: pd.DataFrame,
    devices: Iterable[str],
    *,
    n_splits: int,
    seed: int,
) -> dict[str, int]:
    """Create deterministic, target-stratified folds at device level."""

    device_list = sorted(set(devices))
    device_target = (
        data.loc[data[DEVICE_COLUMN].isin(device_list)]
        .groupby(DEVICE_COLUMN)[TARGET_COLUMN]
        .max()
    )
    rng = np.random.default_rng(seed)
    result: dict[str, int] = {}
    for target_value in (1, 0):
        group = device_target[device_target.eq(target_value)].index.to_numpy()
        group = group.copy()
        rng.shuffle(group)
        for index, device in enumerate(group):
            result[str(device)] = index % n_splits
    return result


def _safe_pr_auc(y_true: np.ndarray, scores: np.ndarray) -> float:
    if np.unique(y_true).size < 2:
        return float("nan")
    return float(average_precision_score(y_true, scores))


def tune_c_by_device(
    data: pd.DataFrame,
    train_devices: Iterable[str],
    features: list[str],
    *,
    c_grid: Iterable[float] = DEFAULT_C_GRID,
    seed: int = 42,
    n_splits: int = 4,
) -> tuple[float, pd.DataFrame]:
    """Tune regularization by inner device folds, never by random rows."""

    train_devices = sorted(set(train_devices))
    fold_map = _device_fold_map(
        data, train_devices, n_splits=n_splits, seed=seed
    )
    rows: list[dict[str, Any]] = []
    for c_value in c_grid:
        for inner_fold in range(n_splits):
            validation_devices = {
                device for device, fold in fold_map.items() if fold == inner_fold
            }
            validation_mask = data[DEVICE_COLUMN].isin(validation_devices)
            training_mask = data[DEVICE_COLUMN].isin(train_devices) & ~validation_mask
            validation_mask &= data[DEVICE_COLUMN].isin(train_devices)
            model = make_logistic_pipeline(float(c_value))
            model.fit(
                data.loc[training_mask, features],
                data.loc[training_mask, TARGET_COLUMN],
            )
            probability = model.predict_proba(
                data.loc[validation_mask, features]
            )[:, 1]
            rows.append(
                {
                    "c_value": float(c_value),
                    "inner_fold": inner_fold,
                    "pr_auc": _safe_pr_auc(
                        data.loc[validation_mask, TARGET_COLUMN].to_numpy(),
                        probability,
                    ),
                }
            )

    scores = pd.DataFrame(rows)
    summary = (
        scores.groupby("c_value", as_index=False)["pr_auc"]
        .agg(["mean", "std"])
        .reset_index()
        .sort_values(["mean", "std", "c_value"], ascending=[False, True, True])
    )
    return float(summary.iloc[0]["c_value"]), scores


def device_oof_probabilities(
    data: pd.DataFrame,
    devices: Iterable[str],
    features: list[str],
    c_value: float,
    *,
    n_splits: int = 4,
    seed: int = 42,
) -> pd.Series:
    """Generate out-of-fold probabilities for calibration without leakage."""

    devices = sorted(set(devices))
    fold_map = _device_fold_map(
        data, devices, n_splits=n_splits, seed=seed
    )
    eligible = data[DEVICE_COLUMN].isin(devices)
    result = pd.Series(np.nan, index=data.index, dtype=float)
    for inner_fold in range(n_splits):
        validation_devices = {
            device for device, fold in fold_map.items() if fold == inner_fold
        }
        validation_mask = eligible & data[DEVICE_COLUMN].isin(validation_devices)
        training_mask = eligible & ~validation_mask
        model = make_logistic_pipeline(c_value)
        model.fit(
            data.loc[training_mask, features],
            data.loc[training_mask, TARGET_COLUMN],
        )
        result.loc[validation_mask] = model.predict_proba(
            data.loc[validation_mask, features]
        )[:, 1]
    if result.loc[eligible].isna().any():
        raise RuntimeError("failed to generate complete device OOF probabilities")
    return result.loc[eligible]


def _logit(probability: np.ndarray) -> np.ndarray:
    clipped = np.clip(np.asarray(probability, dtype=float), 1e-6, 1 - 1e-6)
    return np.log(clipped / (1 - clipped)).reshape(-1, 1)


def fit_calibrator(
    method: str, raw_probability: np.ndarray, y_true: np.ndarray
) -> Any:
    """Fit a probability calibrator; uncalibrated mode stores no object."""

    if method == "uncalibrated":
        return None
    if method == "platt":
        model = LogisticRegression(C=1_000_000, solver="lbfgs", max_iter=2_000)
        return model.fit(_logit(raw_probability), y_true)
    if method == "isotonic":
        model = IsotonicRegression(out_of_bounds="clip")
        return model.fit(np.asarray(raw_probability), y_true)
    raise ValueError(f"unsupported calibration method: {method}")


def apply_calibrator(
    method: str, calibrator: Any, raw_probability: np.ndarray
) -> np.ndarray:
    """Apply a fitted calibrator while enforcing valid probability bounds."""

    raw = np.asarray(raw_probability, dtype=float)
    if method == "uncalibrated":
        calibrated = raw
    elif method == "platt":
        calibrated = calibrator.predict_proba(_logit(raw))[:, 1]
    elif method == "isotonic":
        calibrated = calibrator.predict(raw)
    else:
        raise ValueError(f"unsupported calibration method: {method}")
    return np.clip(np.asarray(calibrated, dtype=float), 0.0, 1.0)


def expected_calibration_error(
    y_true: np.ndarray, probability: np.ndarray, *, bins: int = 10
) -> float:
    """Return equal-width expected calibration error."""

    y = np.asarray(y_true)
    p = np.asarray(probability)
    edges = np.linspace(0.0, 1.0, bins + 1)
    bucket = np.clip(np.digitize(p, edges[1:-1]), 0, bins - 1)
    error = 0.0
    for index in range(bins):
        mask = bucket == index
        if mask.any():
            error += mask.mean() * abs(y[mask].mean() - p[mask].mean())
    return float(error)


def probability_metrics(
    y_true: np.ndarray, probability: np.ndarray
) -> dict[str, float]:
    """Calculate ranking and calibration metrics together."""

    y = np.asarray(y_true, dtype=int)
    p = np.clip(np.asarray(probability, dtype=float), 1e-12, 1 - 1e-12)
    return {
        "pr_auc": _safe_pr_auc(y, p),
        "roc_auc": float(roc_auc_score(y, p)),
        "brier": float(brier_score_loss(y, p)),
        "log_loss": float(log_loss(y, p, labels=[0, 1])),
        "ece_10": expected_calibration_error(y, p, bins=10),
    }


def run_nested_device_comparison(
    inputs: ComparisonInputs,
    *,
    c_grid: Iterable[float] = DEFAULT_C_GRID,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Compare both feature groups with nested, device-exclusive validation."""

    data = inputs.data.copy()
    fold_map = inputs.fold_assignment.set_index(DEVICE_COLUMN)[
        "outer_fold"
    ].to_dict()
    data["outer_fold"] = data[DEVICE_COLUMN].map(fold_map)
    metric_rows: list[dict[str, Any]] = []
    prediction_rows: list[pd.DataFrame] = []
    tuning_rows: list[pd.DataFrame] = []

    for group_name, features in inputs.feature_groups.items():
        for outer_fold in sorted(data["outer_fold"].unique()):
            training = data.loc[data["outer_fold"].ne(outer_fold)]
            validation = data.loc[data["outer_fold"].eq(outer_fold)]
            train_devices = training[DEVICE_COLUMN].unique()
            selected_c, tuning = tune_c_by_device(
                training,
                train_devices,
                features,
                c_grid=c_grid,
                seed=1_000 + int(outer_fold),
            )
            tuning["feature_group"] = group_name
            tuning["outer_fold"] = int(outer_fold)
            tuning_rows.append(tuning)

            inner_oof = device_oof_probabilities(
                training,
                train_devices,
                features,
                selected_c,
                seed=2_000 + int(outer_fold),
            )
            model = make_logistic_pipeline(selected_c)
            model.fit(training[features], training[TARGET_COLUMN])
            raw = model.predict_proba(validation[features])[:, 1]
            prediction = validation[KEY_COLUMNS].copy()
            prediction["feature_group"] = group_name
            prediction["outer_fold"] = int(outer_fold)
            prediction["selected_c"] = selected_c

            for method in CALIBRATION_METHODS:
                calibrator = fit_calibrator(
                    method,
                    inner_oof.to_numpy(),
                    training.loc[inner_oof.index, TARGET_COLUMN].to_numpy(),
                )
                probability = apply_calibrator(method, calibrator, raw)
                prediction[f"probability_{method}"] = probability
                metric_rows.append(
                    {
                        "feature_group": group_name,
                        "feature_count": len(features),
                        "outer_fold": int(outer_fold),
                        "selected_c": selected_c,
                        "calibration": method,
                        "rows": len(validation),
                        "positive_rows": int(validation[TARGET_COLUMN].sum()),
                        **probability_metrics(
                            validation[TARGET_COLUMN].to_numpy(), probability
                        ),
                    }
                )
            prediction_rows.append(prediction)

    metrics = pd.DataFrame(metric_rows)
    predictions = pd.concat(prediction_rows, ignore_index=True)
    tuning = pd.concat(tuning_rows, ignore_index=True)
    return metrics, predictions, tuning


def summarize_comparison(
    fold_metrics: pd.DataFrame, predictions: pd.DataFrame
) -> pd.DataFrame:
    """Create fold-average and pooled OOF metrics for model selection."""

    rows: list[dict[str, Any]] = []
    for (group, calibration), metrics in fold_metrics.groupby(
        ["feature_group", "calibration"]
    ):
        subset = predictions.loc[predictions["feature_group"].eq(group)]
        pooled = probability_metrics(
            subset[TARGET_COLUMN].to_numpy(),
            subset[f"probability_{calibration}"].to_numpy(),
        )
        rows.append(
            {
                "feature_group": group,
                "feature_count": int(metrics["feature_count"].iloc[0]),
                "calibration": calibration,
                **{f"mean_fold_{key}": float(metrics[key].mean()) for key in pooled},
                **{f"pooled_oof_{key}": value for key, value in pooled.items()},
            }
        )
    return pd.DataFrame(rows).sort_values(
        ["feature_group", "calibration"]
    ).reset_index(drop=True)


def run_time_holdout_validation(
    inputs: ComparisonInputs,
    *,
    c_grid: Iterable[float] = DEFAULT_C_GRID,
) -> tuple[pd.DataFrame, pd.DataFrame, dict[str, float]]:
    """Tune on March and evaluate once on the untouched April holdout."""

    data = inputs.data.copy()
    month = data[TIME_COLUMN].dt.month
    jan_feb = data.loc[month.le(2)]
    march = data.loc[month.eq(3)]
    pre_april = data.loc[month.le(3)]
    april = data.loc[month.eq(4)]
    if march[TARGET_COLUMN].nunique() < 2 or april[TARGET_COLUMN].nunique() < 2:
        raise ValueError("March and April must each contain both target classes")

    metric_rows: list[dict[str, Any]] = []
    prediction_rows: list[pd.DataFrame] = []
    selected_c_values: dict[str, float] = {}
    for group_name, features in inputs.feature_groups.items():
        march_scores: list[tuple[float, float]] = []
        for c_value in c_grid:
            model = make_logistic_pipeline(float(c_value))
            model.fit(jan_feb[features], jan_feb[TARGET_COLUMN])
            probability = model.predict_proba(march[features])[:, 1]
            march_scores.append(
                (float(c_value), _safe_pr_auc(march[TARGET_COLUMN], probability))
            )
        selected_c = sorted(march_scores, key=lambda item: (-item[1], item[0]))[0][0]
        selected_c_values[group_name] = selected_c

        pre_oof = device_oof_probabilities(
            pre_april,
            pre_april[DEVICE_COLUMN].unique(),
            features,
            selected_c,
            n_splits=5,
            seed=3_000,
        )
        model = make_logistic_pipeline(selected_c)
        model.fit(pre_april[features], pre_april[TARGET_COLUMN])
        april_raw = model.predict_proba(april[features])[:, 1]
        prediction = april[KEY_COLUMNS].copy()
        prediction["feature_group"] = group_name
        prediction["selected_c"] = selected_c
        for method in CALIBRATION_METHODS:
            calibrator = fit_calibrator(
                method,
                pre_oof.to_numpy(),
                pre_april.loc[pre_oof.index, TARGET_COLUMN].to_numpy(),
            )
            probability = apply_calibrator(method, calibrator, april_raw)
            prediction[f"probability_{method}"] = probability
            metric_rows.append(
                {
                    "feature_group": group_name,
                    "feature_count": len(features),
                    "calibration": method,
                    "selected_c": selected_c,
                    "rows": len(april),
                    "positive_rows": int(april[TARGET_COLUMN].sum()),
                    **probability_metrics(april[TARGET_COLUMN], probability),
                }
            )
        prediction_rows.append(prediction)
    return (
        pd.DataFrame(metric_rows),
        pd.concat(prediction_rows, ignore_index=True),
        selected_c_values,
    )


def select_feature_group(
    comparison_summary: pd.DataFrame,
    time_metrics: pd.DataFrame,
    *,
    tolerance: float = 0.02,
) -> tuple[str, pd.DataFrame]:
    """Choose the smallest group within tolerance of the best balanced PR-AUC."""

    device = comparison_summary.loc[
        comparison_summary["calibration"].eq("uncalibrated"),
        ["feature_group", "feature_count", "pooled_oof_pr_auc"],
    ]
    temporal = time_metrics.loc[
        time_metrics["calibration"].eq("uncalibrated"),
        ["feature_group", "pr_auc"],
    ].rename(columns={"pr_auc": "april_pr_auc"})
    decision = device.merge(temporal, on="feature_group", validate="one_to_one")
    decision["balanced_pr_auc"] = (
        decision["pooled_oof_pr_auc"] + decision["april_pr_auc"]
    ) / 2
    best = float(decision["balanced_pr_auc"].max())
    decision["within_tolerance"] = decision["balanced_pr_auc"].ge(
        best - tolerance
    )
    chosen = decision.loc[decision["within_tolerance"]].sort_values(
        ["feature_count", "balanced_pr_auc"], ascending=[True, False]
    ).iloc[0]
    return str(chosen["feature_group"]), decision


def select_calibration_method(
    comparison_summary: pd.DataFrame,
    feature_group: str,
    *,
    platt_tolerance: float = 0.001,
) -> tuple[str, pd.DataFrame]:
    """Select calibration only from device OOF Brier score.

    Platt is preferred when its Brier score is practically tied with the best
    option because it is stable, monotonic, and easier to monitor.
    """

    candidates = comparison_summary.loc[
        comparison_summary["feature_group"].eq(feature_group)
    ].copy()
    best_brier = float(candidates["pooled_oof_brier"].min())
    platt = candidates.loc[candidates["calibration"].eq("platt")].iloc[0]
    if float(platt["pooled_oof_brier"]) <= best_brier + platt_tolerance:
        selected = "platt"
    else:
        selected = str(
            candidates.sort_values("pooled_oof_brier").iloc[0]["calibration"]
        )
    candidates["selected"] = candidates["calibration"].eq(selected)
    return selected, candidates


def _alert_episodes(
    data: pd.DataFrame, threshold: float, *, gap_hours: int = 24
) -> pd.DataFrame:
    alerts = data.loc[data["probability"].ge(threshold)].sort_values(
        [DEVICE_COLUMN, TIME_COLUMN]
    )
    if alerts.empty:
        return pd.DataFrame(columns=[DEVICE_COLUMN, "alert_time"])
    gap = alerts.groupby(DEVICE_COLUMN)[TIME_COLUMN].diff()
    new_episode = gap.isna() | gap.gt(pd.Timedelta(hours=gap_hours))
    alerts = alerts.assign(_episode=new_episode.groupby(alerts[DEVICE_COLUMN]).cumsum())
    return (
        alerts.groupby([DEVICE_COLUMN, "_episode"], as_index=False)[TIME_COLUMN]
        .min()
        .rename(columns={TIME_COLUMN: "alert_time"})
    )


def threshold_metrics(
    scored_data: pd.DataFrame,
    failure_events: pd.DataFrame,
    threshold: float,
) -> dict[str, float | int]:
    """Evaluate a threshold using row metrics and de-duplicated alert episodes."""

    predicted = scored_data["probability"].ge(threshold).astype(int)
    y_true = scored_data[TARGET_COLUMN].astype(int)
    episodes = _alert_episodes(scored_data, threshold)
    start = scored_data[TIME_COLUMN].min()
    end = scored_data[TIME_COLUMN].max()
    devices = set(scored_data[DEVICE_COLUMN])
    events = failure_events.loc[
        failure_events[DEVICE_COLUMN].isin(devices)
        & failure_events["failure_time"].between(start, end)
    ]

    matched_episodes: set[int] = set()
    detected_events = 0
    for _, event in events.iterrows():
        window_start = event["failure_time"] - pd.Timedelta(hours=168)
        match = episodes.loc[
            episodes[DEVICE_COLUMN].eq(event[DEVICE_COLUMN])
            & episodes["alert_time"].ge(window_start)
            & episodes["alert_time"].lt(event["failure_time"])
        ]
        if not match.empty:
            detected_events += 1
            matched_episodes.update(match.index.tolist())

    device_months = (
        scored_data.assign(_month=scored_data[TIME_COLUMN].dt.to_period("M"))
        [[DEVICE_COLUMN, "_month"]]
        .drop_duplicates()
        .shape[0]
    )
    false_episodes = max(0, len(episodes) - len(matched_episodes))
    return {
        "threshold": float(threshold),
        "row_precision": float(precision_score(y_true, predicted, zero_division=0)),
        "row_recall": float(recall_score(y_true, predicted, zero_division=0)),
        "row_f2": float(fbeta_score(y_true, predicted, beta=2, zero_division=0)),
        "alert_rows": int(predicted.sum()),
        "alert_episodes": int(len(episodes)),
        "failure_events": int(len(events)),
        "detected_events": int(detected_events),
        "event_recall": (
            float(detected_events / len(events)) if len(events) else float("nan")
        ),
        "false_alert_episodes": int(false_episodes),
        "false_alerts_per_device_month": (
            float(false_episodes / device_months) if device_months else float("nan")
        ),
    }


def select_alert_thresholds(
    scored_data: pd.DataFrame,
    failure_events: pd.DataFrame,
) -> tuple[dict[str, float], pd.DataFrame]:
    """Freeze Warning/Critical thresholds using only pre-holdout predictions."""

    quantiles = np.linspace(0.50, 0.999, 250)
    candidates = np.unique(
        np.concatenate(
            [
                np.quantile(scored_data["probability"], quantiles),
                np.array([0.5]),
            ]
        )
    )
    grid = pd.DataFrame(
        [threshold_metrics(scored_data, failure_events, value) for value in candidates]
    )

    warning_pool = grid.loc[grid["event_recall"].ge(0.90)]
    if warning_pool.empty:
        warning_pool = grid.loc[grid["event_recall"].eq(grid["event_recall"].max())]
    warning = warning_pool.sort_values(
        ["row_f2", "false_alerts_per_device_month", "threshold"],
        ascending=[False, True, False],
    ).iloc[0]

    critical_pool = grid.loc[
        grid["event_recall"].ge(0.80)
        & grid["threshold"].ge(float(warning["threshold"]))
    ]
    if critical_pool.empty:
        critical_pool = grid.loc[grid["threshold"].ge(float(warning["threshold"]))]
    critical = critical_pool.sort_values(
        ["false_alerts_per_device_month", "row_precision", "threshold"],
        ascending=[True, False, False],
    ).iloc[0]

    return {
        "warning": float(warning["threshold"]),
        "critical": float(critical["threshold"]),
    }, grid

