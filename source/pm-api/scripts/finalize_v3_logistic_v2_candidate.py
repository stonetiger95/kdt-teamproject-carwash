"""Finalize docs and hashes after the expensive v3 training run.

This script intentionally has no optional Markdown-table dependency, so it can
resume packaging when model training succeeded but ``tabulate`` was absent.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

import joblib
import pandas as pd


PROJECT_DIR = Path(__file__).resolve().parents[1]
OUTPUT = PROJECT_DIR / "outputs" / "13_logistic_v2_candidate"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path: Path, payload: Any) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def markdown_table(
    frame: pd.DataFrame, columns: list[str], *, digits: int = 5
) -> str:
    """Render the small audit tables without a third-party dependency."""

    labels = [column.replace("_", " ") for column in columns]
    lines = [
        "| " + " | ".join(labels) + " |",
        "| " + " | ".join(["---"] * len(columns)) + " |",
    ]
    for _, row in frame[columns].iterrows():
        values = []
        for column in columns:
            value = row[column]
            if isinstance(value, float):
                values.append(f"{value:.{digits}f}")
            else:
                values.append(str(value))
        lines.append("| " + " | ".join(values) + " |")
    return "\n".join(lines)


def main() -> None:
    config = json.loads(
        (OUTPUT / "selected_model_config.json").read_text(encoding="utf-8")
    )
    validation = json.loads(
        (OUTPUT / "validation_metrics.json").read_text(encoding="utf-8")
    )
    comparison = pd.DataFrame(validation["feature_decision"])
    calibration = pd.DataFrame(validation["calibration_decision"])
    april = pd.DataFrame(validation["april_metrics_selected_group"])
    model = joblib.load(OUTPUT / "pm_model_v2_candidate.joblib")
    calibrator = joblib.load(OUTPUT / "pm_calibrator_v2_candidate.joblib")
    model_iterations = int(model[-1].n_iter_[0])
    if model_iterations >= model[-1].max_iter:
        raise RuntimeError("final candidate model did not converge")

    comparison_table = markdown_table(
        comparison,
        [
            "feature_group",
            "feature_count",
            "pooled_oof_pr_auc",
            "april_pr_auc",
            "balanced_pr_auc",
        ],
    )
    calibration_table = markdown_table(
        calibration,
        [
            "calibration",
            "pooled_oof_brier",
            "pooled_oof_ece_10",
            "selected",
        ],
        digits=6,
    )
    april_table = markdown_table(
        april,
        ["calibration", "pr_auc", "brier", "ece_10"],
        digits=6,
    )
    warning = validation["threshold_validation_april"]["warning"]
    critical = validation["threshold_validation_april"]["critical"]

    model_card = f"""# 예지보전 v3 Logistic v2 후보 모델 카드

## 결론과 상태

- 상태: `{config['bundle_status']}`
- 운영 자동 교체: **금지**
- 선택 모델: 13개 time-safe robust 특징 + L2 Logistic (`C=0.001`)
- 확률 보정: Isotonic
- 최종 모델 수렴: {model_iterations} / {model[-1].max_iter}회
- Warning / Critical: {config['thresholds']['warning']:.6f} /
  {config['thresholds']['critical']:.6f}

이 번들은 재현 가능한 **기술 후보**다. 현재 오경보 수준과 합성 데이터 한계
때문에 운영에 바로 연결할 수 있는 승인본은 아니다.

## 작동 이론

장비별 최초 168시간을 정상 기준선으로 고정하고 센서 원값 7개와 24시간
통계 6개를 robust Z-score로 변환한다. L2 로지스틱 회귀는 이 13개 값의
가중합을 7일 이내 고장 위험 점수로 만들며, 클래스 불균형은 balanced
가중치로 처리한다. 장비 OOF 예측만으로 학습한 Isotonic 보정기가 점수를
확률로 바꾸고, 고정 임계값이 Normal/Warning/Critical 상태를 결정한다.

## 데이터·검증 구조

- 공통 비교: {config['comparison_rows']:,}행, {config['devices']}장비,
  양성 {config['positive_rows']:,}행
- 실패 사건: 독립 합성 사건 {config['failure_events']}건
- 장비 검증: 동일 5-fold, 내부 장비 fold로 C 선택
- 시간 검증: 1~3월 학습·보정·임계값 고정 후 4월 평가
- 13개 기준선 보정: 장비별 최초 168행과 그 구간만의 pooled fallback 사용

## 13개 대 71개 결과

{comparison_table}

13개 특징의 종합 PR-AUC가 0.37172로 71개의 0.35360보다 높았다. 또한
71개는 4월 순위 성능은 높지만 장비 OOF 성능이 크게 낮아 장비 간 일반화
불안정성이 보였다. 사전 규칙(최고값 0.02 이내면 단순 모델 우선)에 따라
13개를 선택했다.

## 확률 보정 결과

{calibration_table}

장비 OOF Brier 기준 사전 규칙은 Isotonic을 선택했다. 다만 4월에서는
Platt가 더 안정적이었다.

{april_table}

이 시간 검증 불일치는 즉시 운영 승인을 막는 핵심 검토 사항이다. Shadow
기간에 Isotonic과 Platt를 병렬 기록하고 실제 데이터로 최종 선택해야 한다.

## 임계값의 4월 결과

- Warning: 사건 재현율 {warning['event_recall']:.3f}, 장비-월당 오경보
  {warning['false_alerts_per_device_month']:.3f}, 행 정밀도
  {warning['row_precision']:.3f}
- Critical: 사건 재현율 {critical['event_recall']:.3f}, 장비-월당 오경보
  {critical['false_alerts_per_device_month']:.3f}, 행 정밀도
  {critical['row_precision']:.3f}

현재 오경보 빈도는 운영 알림으로 사용하기에 높다. 임계값은 코드가 만든
재현 가능한 시작점이며 업무 허용치가 아니다.

## 기술 제약

- 합성 데이터 12건의 실패 사건만 사용했다.
- 새 장비는 168시간 기준선이 쌓이기 전 별도 정책이 필요하다.
- 현재 Windows sklearn 빌드의 BLAS 예측 충돌을 피해 연속 float64 입력과
  수학적으로 동일한 요소별 선형식 계산을 joblib 파이프라인에 포함했다.
- 운영 `pm-api/artifacts`는 변경하지 않았다.
"""
    (OUTPUT / "MODEL_CARD.md").write_text(model_card, encoding="utf-8")

    review = f"""# 사용자 검토 필요 사항

아래 항목에 대한 결정 전에는 후보 모델을 운영에 연결하지 마십시오.

- [ ] **13개 특징 선택 승인** — 장비 OOF 일반화와 단순성은 우세하지만,
  71개 특징의 4월 PR-AUC가 더 높다는 점을 수용할지 결정.
- [ ] **보정기 결정** — 사전 장비 OOF 규칙은 Isotonic, 4월 Brier는
  Platt({april.loc[april['calibration'].eq('platt'), 'brier'].iloc[0]:.6f})가
  Isotonic({april.loc[april['calibration'].eq('isotonic'), 'brier'].iloc[0]:.6f})보다
  좋았다. Shadow에서 병렬 비교할지 승인.
- [ ] **오경보 허용치 지정** — Warning {warning['false_alerts_per_device_month']:.3f},
  Critical {critical['false_alerts_per_device_month']:.3f}회/장비-월은 높다.
  허용 가능한 최대값을 수치로 지정.
- [ ] **미탐 허용치 지정** — 4월 5개 사건 중 각 단계가 4개를 탐지했다.
  Critical 최소 사건 재현율을 결정.
- [ ] **실데이터 외부 검증 계획** — 합성 실패 12건만으로 운영 승인 불가.
  실제 고장·정비 이력의 기간, 책임자, 승인 기준 지정.
- [ ] **신규 장비 168시간 정책** — 기존 모델 유지 / 경보 보류 / 공통 기준선
  중 하나를 결정.
- [ ] **Shadow 운영 승인** — 최소 4주간 정비 지시 없이 확률·경보만 기록하고,
  주간 오경보·미탐·드리프트 검토 후 별도 전환 승인.
"""
    (OUTPUT / "REVIEW_REQUIRED.md").write_text(review, encoding="utf-8")

    runbook = """# 후보 번들 실행 안내

## 재학습

프로젝트의 `pm-api` 디렉터리에서 아래 검증 실행점을 사용한다.

```powershell
python -u scripts\\train_v3_logistic_v2_verified.py
```

## 안전 경계

- 출력은 `outputs/13_logistic_v2_candidate`에만 쓴다.
- `pm-api/artifacts` 운영 파일은 자동 교체하지 않는다.
- 배포 전에 `REVIEW_REQUIRED.md`의 모든 항목을 승인하고 shadow 검증한다.
"""
    (OUTPUT / "RUNBOOK.md").write_text(runbook, encoding="utf-8")

    artifacts = {}
    for path in sorted(OUTPUT.iterdir()):
        if path.name == "artifact_manifest.json" or not path.is_file():
            continue
        artifacts[path.name] = {
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
        }
    write_json(
        OUTPUT / "artifact_manifest.json",
        {
            "bundle_status": config["bundle_status"],
            "production_activation_allowed": False,
            "final_model_iterations": model_iterations,
            "calibrator_type": type(calibrator).__name__,
            "artifacts": artifacts,
        },
    )
    print("candidate documentation and manifest completed")
    print(f"artifacts: {len(artifacts)}")


if __name__ == "__main__":
    main()

