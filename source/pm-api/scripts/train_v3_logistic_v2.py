"""Train and package the reviewed v3 supervised PM candidate.

This command never writes into ``pm-api/artifacts``.  It creates a separate
candidate bundle that must pass user review and shadow operation first.

Example:
    python scripts/train_v3_logistic_v2.py
"""

from __future__ import annotations

import argparse
from dataclasses import replace
import hashlib
import json
from pathlib import Path
import sys
from typing import Any

import joblib
import numpy as np
import pandas as pd


PROJECT_DIR = Path(__file__).resolve().parents[1]
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

from app.v3_model_training import (
    CALIBRATION_METHODS,
    DEVICE_COLUMN,
    TARGET_COLUMN,
    TIME_COLUMN,
    apply_calibrator,
    device_oof_probabilities,
    fit_calibrator,
    load_comparison_inputs,
    make_logistic_pipeline,
    run_nested_device_comparison,
    run_time_holdout_validation,
    select_alert_thresholds,
    select_calibration_method,
    select_feature_group,
    summarize_comparison,
    threshold_metrics,
)
from app.v3_safe_baselines import recompute_time_safe_robust_features


DEFAULT_INPUT = PROJECT_DIR / "outputs" / "12_reproducible_preprocessing"
DEFAULT_OUTPUT = PROJECT_DIR / "outputs" / "13_logistic_v2_candidate"
BUNDLE_STATUS = "candidate_pending_user_review_and_shadow"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare, calibrate, and package the v3 PM candidate."
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def _json_default(value: Any) -> Any:
    if isinstance(value, (np.integer,)):
        return int(value)
    if isinstance(value, (np.floating,)):
        return None if not np.isfinite(value) else float(value)
    if isinstance(value, (pd.Timestamp,)):
        return value.isoformat()
    if isinstance(value, Path):
        return str(value)
    raise TypeError(f"not JSON serializable: {type(value)!r}")


def write_json(path: Path, payload: object) -> None:
    path.write_text(
        json.dumps(
            payload,
            ensure_ascii=False,
            indent=2,
            default=_json_default,
            allow_nan=False,
        ),
        encoding="utf-8",
    )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _records(frame: pd.DataFrame) -> list[dict[str, Any]]:
    return frame.replace({np.nan: None}).to_dict(orient="records")


def build_model_card(
    *,
    config: dict[str, Any],
    comparison_decision: pd.DataFrame,
    calibration_decision: pd.DataFrame,
    april_metrics: pd.DataFrame,
    threshold_validation: dict[str, dict[str, Any]],
) -> str:
    selected = config["selected_feature_group"]
    calibration = config["calibration_method"]
    warning = config["thresholds"]["warning"]
    critical = config["thresholds"]["critical"]
    comparison_md = comparison_decision.to_markdown(index=False, floatfmt=".5f")
    calibration_md = calibration_decision[
        ["calibration", "pooled_oof_brier", "pooled_oof_ece_10", "selected"]
    ].to_markdown(index=False, floatfmt=".6f")
    april_md = april_metrics[
        ["calibration", "pr_auc", "brier", "ece_10"]
    ].to_markdown(index=False, floatfmt=".6f")
    return f"""# 예지보전 v3 Logistic v2 후보 모델 카드

## 상태

- 상태: `{BUNDLE_STATUS}`
- 운영 자동 교체: **금지**
- 선택 특징: `{selected}` ({config['feature_count']}개)
- 확률 보정: `{calibration}`
- Warning / Critical: `{warning:.6f}` / `{critical:.6f}`

## 이론적 구조

센서 원값과 과거 구간 통계를 입력으로 받아 L2 로지스틱 회귀가 7일 이내
고장 가능성의 순위를 계산한다. 결측치는 학습 구간 중앙값으로 대체하고,
표준화 후 장비 단위로 분리한 데이터에서 학습한다. 클래스 불균형은
`class_weight=balanced`로 보정한다. 원시 점수는 장비 OOF 예측만으로 학습한
`{calibration}` 보정기를 통과해 확률로 변환된다. 마지막으로 고정 임계값을
적용해 Normal, Warning, Critical 상태를 만든다.

## 검증 설계

- 공통 비교행: {config['comparison_rows']:,}행 / {config['devices']}장비
- 장비 외부검증: 동일한 5개 장비 폴드, 내부 장비 폴드로 C 선택
- 시간 외부검증: 1~3월 학습 및 보정, 4월 최종 홀드아웃
- 임계값: 1~3월 OOF 예측에서 고정한 뒤 4월에 변경 없이 평가
- 실패 사건: 합성 데이터의 독립 사건 {config['failure_events']}건

## 13개 대 71개 특징 선택

{comparison_md}

최고 균형 PR-AUC와 0.02 이내면 더 단순한 특징군을 선택했다. 이는 작은
합성 실패 사건 수에서 복잡한 특징군을 과신하지 않기 위한 규칙이다.

## 확률 보정 선택

{calibration_md}

보정법은 4월을 보지 않고 장비 OOF Brier 점수로 선택했다. Platt가 최저값과
0.001 이내이면 단조성과 운영 안정성을 위해 Platt를 우선했다.

## 4월 홀드아웃

{april_md}

임계값 평가는 `validation_metrics.json`에 Warning/Critical별 사건 재현율과
장비-월당 오경보 에피소드 수로 저장했다.

## 중요한 한계

- 데이터는 합성이며 독립 실패 사건이 {config['failure_events']}건뿐이다.
- 동일 생성 규칙의 내부 검증이므로 실제 현장 일반화 성능을 보장하지 않는다.
- 새 장비는 168시간 기준선이 없으므로 즉시 robust 특징을 만들 수 없다.
- 이 후보는 운영 모델 파일을 교체하지 않았고 shadow 검증 전에는 사용할 수 없다.
"""


def build_review_required(
    config: dict[str, Any], threshold_validation: dict[str, dict[str, Any]]
) -> str:
    warning = threshold_validation["warning"]
    critical = threshold_validation["critical"]
    return f"""# 사용자 검토 필요 사항

아래 항목을 승인하기 전에는 후보 모델을 운영에 연결하지 마십시오.

1. **특징군 승인**: `{config['selected_feature_group']}`
   ({config['feature_count']}개)가 성능과 운영 복잡도의 균형에 맞는지 검토.
2. **Warning 임계값 승인**: `{config['thresholds']['warning']:.6f}` —
   4월 사건 재현율 {warning['event_recall']:.3f}, 장비-월당 오경보
   {warning['false_alerts_per_device_month']:.3f}.
3. **Critical 임계값 승인**: `{config['thresholds']['critical']:.6f}` —
   4월 사건 재현율 {critical['event_recall']:.3f}, 장비-월당 오경보
   {critical['false_alerts_per_device_month']:.3f}.
4. **합성 데이터 한계 승인**: 독립 실패 사건 {config['failure_events']}건으로
   학습했으므로 실제 고장·정비 이력으로 외부 검증할 계획과 책임자를 지정.
5. **신규 장비 정책 결정**: 최초 168시간 동안 기존 모델 사용, 보수적 기본값,
   또는 경보 보류 중 어떤 정책을 적용할지 결정.
6. **Shadow 운영 조건 결정**: 권장 최소 4주, 알림은 관리자 화면에만 표시하고
   실제 정비 지시는 하지 않으며 오경보·미탐·확률 드리프트를 주간 검토.
7. **전환 승인**: shadow 종료 후 운영 API 계약, 롤백 파일, 모니터링 기준까지
   확인한 뒤 별도 배포 작업으로 승인.
"""


def main() -> None:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    loaded = load_comparison_inputs(args.input)
    safe_data, safe_baselines = recompute_time_safe_robust_features(loaded.data)
    inputs = replace(loaded, data=safe_data)

    print("[1/5] identical-row nested device comparison")
    fold_metrics, device_predictions, tuning = run_nested_device_comparison(
        inputs
    )
    comparison_summary = summarize_comparison(fold_metrics, device_predictions)

    print("[2/5] March tuning and untouched April validation")
    time_metrics, april_predictions, time_c = run_time_holdout_validation(inputs)
    selected_group, feature_decision = select_feature_group(
        comparison_summary, time_metrics
    )
    calibration, calibration_decision = select_calibration_method(
        comparison_summary, selected_group
    )
    features = inputs.feature_groups[selected_group]
    selected_c = time_c[selected_group]

    print("[3/5] freeze calibration and alert thresholds on pre-April OOF")
    pre_april = inputs.data.loc[inputs.data[TIME_COLUMN].dt.month.le(3)]
    pre_oof = device_oof_probabilities(
        pre_april,
        pre_april[DEVICE_COLUMN].unique(),
        features,
        selected_c,
        n_splits=5,
        seed=3_000,
    )
    calibrator = fit_calibrator(
        calibration,
        pre_oof.to_numpy(),
        pre_april.loc[pre_oof.index, TARGET_COLUMN].to_numpy(),
    )
    pre_scored = pre_april.loc[
        pre_oof.index, [DEVICE_COLUMN, TIME_COLUMN, TARGET_COLUMN]
    ].copy()
    pre_scored["probability"] = apply_calibrator(
        calibration, calibrator, pre_oof.to_numpy()
    )
    thresholds, threshold_grid = select_alert_thresholds(
        pre_scored, inputs.failure_events
    )

    selected_april = april_predictions.loc[
        april_predictions["feature_group"].eq(selected_group),
        [DEVICE_COLUMN, TIME_COLUMN, TARGET_COLUMN, f"probability_{calibration}"],
    ].rename(columns={f"probability_{calibration}": "probability"})
    threshold_validation = {
        level: threshold_metrics(
            selected_april, inputs.failure_events, threshold
        )
        for level, threshold in thresholds.items()
    }

    print("[4/5] refit base model on all common rows and write candidate bundle")
    final_model = make_logistic_pipeline(selected_c)
    final_model.fit(inputs.data[features], inputs.data[TARGET_COLUMN])
    model_path = args.output / "pm_model_v2_candidate.joblib"
    calibrator_path = args.output / "pm_calibrator_v2_candidate.joblib"
    joblib.dump(final_model, model_path)
    joblib.dump(calibrator, calibrator_path)

    safe_baselines.to_parquet(
        args.output / "device_baselines_time_safe.parquet", index=False
    )
    fold_metrics.to_csv(args.output / "device_fold_metrics.csv", index=False)
    comparison_summary.to_csv(
        args.output / "comparison_summary.csv", index=False
    )
    tuning.to_csv(args.output / "inner_tuning_results.csv", index=False)
    device_predictions.to_parquet(
        args.output / "device_oof_predictions.parquet", index=False
    )
    time_metrics.to_csv(args.output / "april_holdout_metrics.csv", index=False)
    april_predictions.to_parquet(
        args.output / "april_holdout_predictions.parquet", index=False
    )
    threshold_grid.to_csv(args.output / "threshold_search.csv", index=False)
    pre_scored.to_parquet(
        args.output / "pre_april_oof_calibrated.parquet", index=False
    )

    config = {
        "bundle_version": "logistic-v2-candidate",
        "bundle_status": BUNDLE_STATUS,
        "production_activation_allowed": False,
        "preprocessing_schema": "v3-preprocessing-1.0.0+time-safe-baseline",
        "selected_feature_group": selected_group,
        "feature_count": len(features),
        "feature_names": features,
        "model": "L2 logistic regression",
        "c_value": selected_c,
        "class_weight": "balanced",
        "calibration_method": calibration,
        "calibration_fit_period": "2026-01-01 through 2026-03-31 OOF",
        "thresholds": thresholds,
        "threshold_fit_period": "2026-01-01 through 2026-03-31 OOF",
        "comparison_rows": len(inputs.data),
        "devices": int(inputs.data[DEVICE_COLUMN].nunique()),
        "positive_rows": int(inputs.data[TARGET_COLUMN].sum()),
        "failure_events": len(inputs.failure_events),
        "baseline_hours": 168,
        "unknown_device_policy": "requires_user_decision",
        "operating_model_replaced": False,
    }
    validation = {
        "selection_policy": {
            "feature_group": (
                "highest mean of pooled device OOF and April PR-AUC; choose "
                "fewer features within 0.02"
            ),
            "calibration": (
                "minimum device OOF Brier; prefer Platt within 0.001"
            ),
            "warning": "maximize row F2 subject to event recall >= 0.90",
            "critical": (
                "minimize false alert episodes/device-month subject to event "
                "recall >= 0.80 and threshold >= Warning"
            ),
        },
        "feature_decision": _records(feature_decision),
        "calibration_decision": _records(calibration_decision),
        "april_metrics_selected_group": _records(
            time_metrics.loc[time_metrics["feature_group"].eq(selected_group)]
        ),
        "threshold_validation_april": threshold_validation,
    }
    write_json(args.output / "selected_model_config.json", config)
    write_json(args.output / "validation_metrics.json", validation)
    write_json(
        args.output / "feature_contract.json",
        {
            "feature_names_in_order": features,
            "robust_baseline_rule": (
                "per-device first 168 eligible rows; pooled fallback also "
                "limited to those first-168 windows"
            ),
            "rolling_windows_include_current_row": True,
            "future_values_used": False,
        },
    )

    selected_april_metrics = time_metrics.loc[
        time_metrics["feature_group"].eq(selected_group)
    ]
    model_card = build_model_card(
        config=config,
        comparison_decision=feature_decision,
        calibration_decision=calibration_decision,
        april_metrics=selected_april_metrics,
        threshold_validation=threshold_validation,
    )
    (args.output / "MODEL_CARD.md").write_text(model_card, encoding="utf-8")
    (args.output / "REVIEW_REQUIRED.md").write_text(
        build_review_required(config, threshold_validation), encoding="utf-8"
    )

    print("[5/5] hash every artifact")
    artifacts = {}
    for path in sorted(args.output.iterdir()):
        if path.name == "artifact_manifest.json" or not path.is_file():
            continue
        artifacts[path.name] = {
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
        }
    write_json(
        args.output / "artifact_manifest.json",
        {
            "bundle_status": BUNDLE_STATUS,
            "source_directory": str(args.input.resolve()),
            "artifacts": artifacts,
        },
    )

    print("v3 Logistic v2 candidate completed")
    print(f"output: {args.output}")
    print(f"selected: {selected_group} / C={selected_c} / {calibration}")
    print(
        "thresholds: "
        f"warning={thresholds['warning']:.6f}, "
        f"critical={thresholds['critical']:.6f}"
    )
    print(f"status: {BUNDLE_STATUS}")


if __name__ == "__main__":
    main()

