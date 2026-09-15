from pathlib import Path
import json

import joblib
import numpy as np
import pandas as pd

from .schemas import PredictionRequest, PredictionResponse


class ModelService:

    def __init__(self):
        project_dir = Path(__file__).resolve().parents[1]
        artifact_dir = project_dir / "artifacts"

        config_path = artifact_dir / "model_config.json"
        model_path = artifact_dir / "pm_model.joblib"
        calibrator_path = artifact_dir / "pm_calibrator.joblib"

        with config_path.open("r", encoding="utf-8") as file:
            self.config = json.load(file)

        self.model = joblib.load(model_path)
        self.calibrator = joblib.load(calibrator_path)

        self.features = self.config["features"]
        self.model_version = self.config["model_version"]

        request_features = set(PredictionRequest.model_fields) - {
            "equipment_id",
        }
        unsupported_features = set(self.features) - request_features

        if unsupported_features:
            raise ValueError(
                "모델 feature와 API 입력 필드가 일치하지 않습니다: "
                f"{sorted(unsupported_features)}"
            )

        thresholds = self.config["thresholds"]

        self.warning_threshold = thresholds["warning_f2"]
        self.critical_threshold = thresholds[
            "critical_low_false_alert"
        ]

        self.is_loaded = True

    @staticmethod
    def clipped_logit(probability: float) -> np.ndarray:
        probability = np.clip(
            np.asarray([probability], dtype=float),
            1e-6,
            1 - 1e-6,
        )

        return np.log(
            probability / (1 - probability)
        ).reshape(-1, 1)

    def predict(
        self,
        request: PredictionRequest,
    ) -> PredictionResponse:

        request_data = request.model_dump()

        feature_data = {
            feature: request_data[feature]
            for feature in self.features
        }

        feature_frame = pd.DataFrame(
            [feature_data],
            columns=self.features,
        )

        raw_probability = float(
            self.model.predict_proba(feature_frame)[0, 1]
        )

        calibrated_probability = float(
            self.calibrator.predict_proba(
                self.clipped_logit(raw_probability)
            )[0, 1]
        )

        risk_score = round(
            calibrated_probability * 100,
            2,
        )

        if calibrated_probability >= self.critical_threshold:
            risk_level = "CRITICAL"
        elif calibrated_probability >= self.warning_threshold:
            risk_level = "WARNING"
        else:
            risk_level = "NORMAL"

        return PredictionResponse(
            equipment_id=request.equipment_id,
            raw_probability=raw_probability,
            calibrated_probability=calibrated_probability,
            risk_score=risk_score,
            risk_level=risk_level,
            model_version=self.model_version,
        )
