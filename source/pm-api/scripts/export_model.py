from pathlib import Path
import json

import joblib
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler


PROJECT_DIR = Path(__file__).resolve().parents[1]
WORKSPACE_DIR = PROJECT_DIR / "outputs"

DATA_PATH = (
    WORKSPACE_DIR
    / "03_baseline_residuals"
    / "predictive_maintenance_features.parquet"
)

FOLD_PATH = (
    WORKSPACE_DIR
    / "04_failure_prediction"
    / "device_fold_assignment.csv"
)

CONFIG_PATH = (
    WORKSPACE_DIR
    / "07_probability_calibration"
    / "final_model_config.json"
)

ARTIFACT_DIR = PROJECT_DIR / "artifacts"

TARGET_COLUMN = "failure_within_7d"
DEVICE_COLUMN = "equipment_id"
FOLD_COLUMN = "outer_fold"


def make_base_model():
    return Pipeline(
        [
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
            (
                "model",
                LogisticRegression(
                    penalty="l2",
                    C=0.001,
                    class_weight="balanced",
                    solver="liblinear",
                    max_iter=1500,
                    random_state=42,
                ),
            ),
        ]
    )


def clipped_logit(probability):
    probability = np.clip(
        np.asarray(probability, dtype=float),
        1e-6,
        1 - 1e-6,
    )

    return np.log(probability / (1 - probability)).reshape(-1, 1)


def main():
    print("PM_BUILD 데이터를 읽습니다.")

    data = pd.read_parquet(DATA_PATH)
    folds = pd.read_csv(FOLD_PATH)

    with CONFIG_PATH.open("r", encoding="utf-8") as file:
        model_config = json.load(file)

    features = model_config["features"]

    required_columns = (
        [DEVICE_COLUMN, TARGET_COLUMN, FOLD_COLUMN]
        + features
    )

    missing_columns = [
        column
        for column in required_columns
        if column not in data.columns
        and column not in folds.columns
    ]

    if missing_columns:
        raise ValueError(
            f"필수 컬럼이 없습니다: {missing_columns}"
        )

    frame = data.merge(
        folds[[DEVICE_COLUMN, FOLD_COLUMN]],
        on=DEVICE_COLUMN,
        how="left",
        validate="many_to_one",
    )

    if frame[FOLD_COLUMN].isna().any():
        raise ValueError("일부 장비의 fold 정보가 없습니다.")

    y = frame[TARGET_COLUMN].astype(int)

    print(f"전체 데이터 수: {len(frame)}")
    print(f"사용 특성: {features}")
    print(f"타깃 컬럼: {TARGET_COLUMN}")

    # 장비 단위 외부 fold의 검증 데이터를 이용해
    # calibration용 out-of-fold raw probability를 생성합니다.
    oof_raw_probability = pd.Series(
        index=frame.index,
        dtype=float,
    )

    for fold in sorted(frame[FOLD_COLUMN].unique()):
        train_mask = frame[FOLD_COLUMN] != fold
        validation_mask = frame[FOLD_COLUMN] == fold

        train_data = frame.loc[train_mask]
        validation_data = frame.loc[validation_mask]

        base_model = make_base_model()

        base_model.fit(
            train_data[features],
            train_data[TARGET_COLUMN],
        )

        raw_probability = base_model.predict_proba(
            validation_data[features]
        )[:, 1]

        oof_raw_probability.loc[validation_data.index] = raw_probability

        print(
            f"fold={fold}, "
            f"train={len(train_data)}, "
            f"validation={len(validation_data)}"
        )

    if oof_raw_probability.isna().any():
        raise ValueError(
            "OOF probability가 생성되지 않은 데이터가 있습니다."
        )

    # Platt calibration 모델을 학습합니다.
    calibrator = LogisticRegression(
        C=1e6,
        solver="lbfgs",
        max_iter=2000,
        random_state=42,
    )

    calibrator.fit(
        clipped_logit(oof_raw_probability.to_numpy()),
        y.to_numpy(),
    )

    # 운영용 기본 모델은 전체 데이터로 다시 학습합니다.
    final_model = make_base_model()

    final_model.fit(
        frame[features],
        y,
    )

    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)

    joblib.dump(
        final_model,
        ARTIFACT_DIR / "pm_model.joblib",
    )

    joblib.dump(
        calibrator,
        ARTIFACT_DIR / "pm_calibrator.joblib",
    )

    exported_config = {
        **model_config,
        "model_version": "logistic-platt-v1",
        "target_column": TARGET_COLUMN,
        "device_column": DEVICE_COLUMN,
        "trained_row_count": len(frame),
        "artifact_files": {
            "model": "pm_model.joblib",
            "calibrator": "pm_calibrator.joblib",
        },
    }

    with (
        ARTIFACT_DIR / "model_config.json"
    ).open("w", encoding="utf-8") as file:
        json.dump(
            exported_config,
            file,
            ensure_ascii=False,
            indent=2,
        )

    print("모델 artifact 저장이 완료되었습니다.")
    print(f"저장 위치: {ARTIFACT_DIR}")


if __name__ == "__main__":
    main()
