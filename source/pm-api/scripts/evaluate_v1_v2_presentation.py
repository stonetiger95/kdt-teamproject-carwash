"""Reproduce the presentation comparison between production v1 and candidate v2.

The comparison uses exactly the same untouched April v3 rows for both models.
The v2 evaluation model is refit only on January-March data; this is important
because the deployable candidate artifact was later refit on all available rows.
The frozen v2 calibrator and thresholds were also selected without April labels.

Outputs are machine-readable audit files under ``pm-api/evaluation``.  A human
presentation summary is maintained separately at the repository root.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
from typing import Any

import joblib
import numpy as np
import pandas as pd


PROJECT_DIR = Path(__file__).resolve().parents[1]
REPOSITORY_DIR = PROJECT_DIR.parent
PREPROCESSING_DIR = PROJECT_DIR / "outputs" / "12_reproducible_preprocessing"
CANDIDATE_DIR = PROJECT_DIR / "outputs" / "13_logistic_v2_candidate"
OUTPUT_DIR = PROJECT_DIR / "evaluation"

if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

from app.v3_model_training import (  # noqa: E402
    DEVICE_COLUMN,
    TARGET_COLUMN,
    TIME_COLUMN,
    apply_calibrator,
    load_comparison_inputs,
    probability_metrics,
    threshold_metrics,
)
from app.v3_safe_baselines import (  # noqa: E402
    recompute_time_safe_robust_features,
)
from app.v3_windows_estimator import (  # noqa: E402
    make_verified_logistic_pipeline,
)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _json_value(value: Any) -> Any:
    if isinstance(value, (np.integer,)):
        return int(value)
    if isinstance(value, (np.floating,)):
        return float(value)
    if isinstance(value, pd.Timestamp):
        return value.isoformat()
    raise TypeError(f"not JSON serializable: {type(value)!r}")


def _score_v1(april: pd.DataFrame) -> tuple[np.ndarray, np.ndarray, dict[str, Any]]:
    """Run the currently served seven-feature v1 artifact on April v3 rows."""

    artifact_dir = PROJECT_DIR / "artifacts"
    config_path = artifact_dir / "model_config.json"
    model_path = artifact_dir / "pm_model.joblib"
    calibrator_path = artifact_dir / "pm_calibrator.joblib"
    config = json.loads(config_path.read_text(encoding="utf-8"))
    model = joblib.load(model_path)
    calibrator = joblib.load(calibrator_path)

    # The host BLAS crashes on a large sklearn matrix product.  Reproduce the
    # fitted binary logistic pipeline explicitly with an elementwise sum; this
    # is algebraically identical to sklearn's decision_function.
    transformed = model.named_steps["imputer"].transform(
        april[config["features"]]
    )
    transformed = model.named_steps["scaler"].transform(transformed)
    classifier = model.named_steps["model"]
    decision = (
        np.sum(transformed * classifier.coef_[0], axis=1)
        + classifier.intercept_[0]
    )
    decision = np.clip(decision, -709.0, 709.0)
    raw = 1.0 / (1.0 + np.exp(-decision))
    clipped = np.clip(raw, 1e-6, 1 - 1e-6)
    logit = np.log(clipped / (1 - clipped)).reshape(-1, 1)
    calibrated_decision = (
        np.sum(logit * calibrator.coef_[0], axis=1)
        + calibrator.intercept_[0]
    )
    calibrated_decision = np.clip(calibrated_decision, -709.0, 709.0)
    calibrated = 1.0 / (1.0 + np.exp(-calibrated_decision))
    audit = {
        "version": config["model_version"],
        "feature_count": len(config["features"]),
        "calibration": config["calibration_method"],
        "thresholds": {
            "warning": float(config["thresholds"]["warning_f2"]),
            "critical": float(
                config["thresholds"]["critical_low_false_alert"]
            ),
        },
        "artifacts": {
            path.name: _sha256(path)
            for path in (config_path, model_path, calibrator_path)
        },
    }
    return raw, calibrated, audit


def _score_v2_leakage_safe(
    pre_april: pd.DataFrame,
    april: pd.DataFrame,
) -> tuple[np.ndarray, np.ndarray, dict[str, Any]]:
    """Refit v2 on Jan-Mar and score April without using April labels."""

    config_path = CANDIDATE_DIR / "selected_model_config.json"
    calibrator_path = CANDIDATE_DIR / "pm_calibrator_v2_candidate.joblib"
    config = json.loads(config_path.read_text(encoding="utf-8"))
    features = list(config["feature_names"])

    # This is the same deterministic Windows-safe estimator used by the
    # verified training command.  Only Jan-Mar rows enter model.fit().
    model = make_verified_logistic_pipeline(float(config["c_value"]))
    model.fit(pre_april[features], pre_april[TARGET_COLUMN])
    raw = model.predict_proba(april[features])[:, 1]

    # The serialized calibrator was fitted from Jan-Mar device-OOF predictions.
    calibrator = joblib.load(calibrator_path)
    calibrated = apply_calibrator(
        str(config["calibration_method"]), calibrator, raw
    )
    audit = {
        "version": config["bundle_version"],
        "feature_count": len(features),
        "calibration": config["calibration_method"],
        "training_period": "2026-01-01 through 2026-03-31",
        "evaluation_period": "2026-04-01 through 2026-04-30",
        "thresholds": {
            key: float(value) for key, value in config["thresholds"].items()
        },
        "production_activation_allowed": bool(
            config["production_activation_allowed"]
        ),
        "artifacts": {
            path.name: _sha256(path)
            for path in (config_path, calibrator_path)
        },
    }
    return raw, calibrated, audit


def _metric_row(
    label: str,
    y_true: pd.Series,
    probability: np.ndarray,
    *,
    feature_count: int,
    calibration: str,
) -> dict[str, Any]:
    return {
        "model": label,
        "feature_count": feature_count,
        "calibration": calibration,
        "rows": int(len(y_true)),
        "positive_rows": int(y_true.sum()),
        **probability_metrics(y_true.to_numpy(), probability),
    }


def _threshold_rows(
    model: str,
    april: pd.DataFrame,
    probability: np.ndarray,
    events: pd.DataFrame,
    thresholds: dict[str, float],
) -> list[dict[str, Any]]:
    scored = april[[DEVICE_COLUMN, TIME_COLUMN, TARGET_COLUMN]].copy()
    scored["probability"] = probability
    return [
        {
            "model": model,
            "level": level,
            **threshold_metrics(scored, events, float(threshold)),
        }
        for level, threshold in thresholds.items()
    ]


def main() -> None:
    loaded = load_comparison_inputs(PREPROCESSING_DIR)
    safe_data, _ = recompute_time_safe_robust_features(loaded.data)
    month = safe_data[TIME_COLUMN].dt.month
    pre_april = safe_data.loc[month.le(3)].copy()
    april = safe_data.loc[month.eq(4)].copy()
    april = april.sort_values([DEVICE_COLUMN, TIME_COLUMN]).reset_index(drop=True)

    v1_raw, v1_probability, v1_audit = _score_v1(april)
    v2_raw, v2_probability, v2_audit = _score_v2_leakage_safe(
        pre_april, april
    )

    # Stored holdout predictions are an independent reproducibility checkpoint.
    frozen = pd.read_parquet(
        CANDIDATE_DIR / "april_holdout_predictions.parquet"
    )
    frozen = frozen.loc[
        frozen["feature_group"].eq("snapshot_13")
    ].sort_values([DEVICE_COLUMN, TIME_COLUMN]).reset_index(drop=True)
    if not april[[DEVICE_COLUMN, TIME_COLUMN, TARGET_COLUMN]].equals(
        frozen[[DEVICE_COLUMN, TIME_COLUMN, TARGET_COLUMN]]
    ):
        raise RuntimeError("April rows do not match the frozen candidate evaluation")
    raw_max_error = float(
        np.max(np.abs(v2_raw - frozen["probability_uncalibrated"].to_numpy()))
    )
    calibrated_max_error = float(
        np.max(np.abs(v2_probability - frozen["probability_isotonic"].to_numpy()))
    )
    if raw_max_error > 1e-9 or calibrated_max_error > 1e-9:
        raise RuntimeError(
            "v2 rerun did not reproduce the frozen April predictions: "
            f"raw={raw_max_error}, calibrated={calibrated_max_error}"
        )

    metrics = pd.DataFrame(
        [
            _metric_row(
                "current_v1_calibrated",
                april[TARGET_COLUMN],
                v1_probability,
                feature_count=v1_audit["feature_count"],
                calibration=v1_audit["calibration"],
            ),
            _metric_row(
                "candidate_v2_raw",
                april[TARGET_COLUMN],
                v2_raw,
                feature_count=v2_audit["feature_count"],
                calibration="uncalibrated",
            ),
            _metric_row(
                "candidate_v2_operational",
                april[TARGET_COLUMN],
                v2_probability,
                feature_count=v2_audit["feature_count"],
                calibration=v2_audit["calibration"],
            ),
        ]
    )
    thresholds = pd.DataFrame(
        _threshold_rows(
            "current_v1",
            april,
            v1_probability,
            loaded.failure_events,
            v1_audit["thresholds"],
        )
        + _threshold_rows(
            "candidate_v2",
            april,
            v2_probability,
            loaded.failure_events,
            v2_audit["thresholds"],
        )
    )

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    metrics.to_csv(OUTPUT_DIR / "presentation_model_comparison.csv", index=False)
    thresholds.to_csv(
        OUTPUT_DIR / "presentation_threshold_comparison.csv", index=False
    )
    audit = {
        "executed_at_local": pd.Timestamp.now().isoformat(),
        "comparison_contract": {
            "same_rows_for_both_models": True,
            "april_rows": len(april),
            "positive_rows": int(april[TARGET_COLUMN].sum()),
            "devices": int(april[DEVICE_COLUMN].nunique()),
            "april_labels_used_for_training": False,
            "v2_frozen_prediction_raw_max_abs_error": raw_max_error,
            "v2_frozen_prediction_calibrated_max_abs_error": calibrated_max_error,
        },
        "v1": v1_audit,
        "v2": v2_audit,
        "metrics": metrics.to_dict(orient="records"),
        "threshold_metrics": thresholds.to_dict(orient="records"),
    }
    (OUTPUT_DIR / "presentation_evaluation.json").write_text(
        json.dumps(
            audit,
            ensure_ascii=False,
            indent=2,
            default=_json_value,
            allow_nan=False,
        ),
        encoding="utf-8",
    )

    print("presentation comparison completed")
    print(
        f"April rows={len(april):,}, positives={int(april[TARGET_COLUMN].sum()):,}, "
        f"devices={april[DEVICE_COLUMN].nunique()}"
    )
    print(metrics.to_string(index=False))
    print(thresholds.to_string(index=False))
    print(
        "v2 reproduction max error: "
        f"raw={raw_max_error:.3e}, calibrated={calibrated_max_error:.3e}"
    )


if __name__ == "__main__":
    main()
