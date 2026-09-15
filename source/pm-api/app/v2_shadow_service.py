"""Online feature construction and inference for the activated v2 model.

It requires 24 contiguous hourly readings and a reviewed 168-hour device
baseline artifact.  The caller explicitly chooses active or shadow mode so the
same verified feature contract is used for both the primary decision and audit.
"""

from __future__ import annotations

import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from .schemas import V2ShadowPredictionRequest, V2ShadowPredictionResponse
from .v3_model_training import apply_calibrator
from .v3_safe_baselines import CORE_FEATURES


ROLLING_DEFINITIONS = {
    "success_rate_mean_24h": ("success_rate", "mean"),
    "response_time_ms_mean_24h": ("response_time_ms", "mean"),
    "packet_loss_rate_mean_24h": ("packet_loss_rate", "mean"),
    "error_count_sum_24h": ("error_count", "sum"),
    "retry_count_sum_24h": ("retry_count", "sum"),
    "signal_strength_dbm_std_24h": ("signal_strength_dbm", "std"),
}


class V2ShadowModelService:
    """Load the reviewed candidate and produce non-authoritative v2 scores."""

    def __init__(self) -> None:
        project_dir = Path(__file__).resolve().parents[1]
        artifact_dir = project_dir / "artifacts" / "v2_candidate"
        config_path = artifact_dir / "selected_model_config.json"
        with config_path.open("r", encoding="utf-8") as file:
            self.config = json.load(file)

        self.production_activation_allowed = bool(
            self.config.get("production_activation_allowed", False)
        )
        self.model = joblib.load(
            artifact_dir / "pm_model_v2_candidate.joblib"
        )
        self.calibrator = joblib.load(
            artifact_dir / "pm_calibrator_v2_candidate.joblib"
        )
        self.baselines = pd.read_parquet(
            artifact_dir / "device_baselines_time_safe.parquet"
        ).set_index("equipment_id")
        self.features = list(self.config["feature_names"])
        self.model_version = self.config["bundle_version"]
        self.calibration_method = self.config["calibration_method"]
        self.warning_threshold = float(self.config["thresholds"]["warning"])
        self.critical_threshold = float(
            self.config["thresholds"]["critical"]
        )
        self.is_loaded = True

    def predict(
        self,
        request: V2ShadowPredictionRequest,
        *,
        shadow_only: bool = True,
    ) -> V2ShadowPredictionResponse:
        if not shadow_only and not self.production_activation_allowed:
            raise ValueError("v2 운영 활성화가 승인되지 않은 번들입니다.")
        rows = pd.DataFrame([item.model_dump() for item in request.readings])
        if not rows["equipment_id"].eq(request.equipment_id).all():
            raise ValueError("readings의 equipment_id가 요청 장비와 다릅니다.")
        rows["collected_at"] = pd.to_datetime(rows["collected_at"])
        rows = rows.sort_values("collected_at").reset_index(drop=True)
        if rows["collected_at"].duplicated().any():
            raise ValueError("동일 장비·시각의 중복 센서 행이 있습니다.")

        contiguous = self._latest_contiguous_segment(rows)
        if len(contiguous) < 24:
            return self._not_scored(
                request.equipment_id,
                "WARMING_UP",
                len(contiguous),
                "v2는 최근 24시간의 연속 센서 로그가 필요합니다.",
                shadow_only,
            )
        if request.equipment_id not in self.baselines.index:
            return self._not_scored(
                request.equipment_id,
                "UNKNOWN_DEVICE",
                len(contiguous),
                "검토된 168시간 장비 기준선이 없어 v2 점수를 보류했습니다.",
                shadow_only,
            )

        feature_frame = self._build_feature_frame(
            contiguous.tail(24), request.equipment_id
        )
        raw_probability = float(self.model.predict_proba(feature_frame)[0, 1])
        calibrated_probability = float(
            apply_calibrator(
                self.calibration_method,
                self.calibrator,
                np.asarray([raw_probability]),
            )[0]
        )
        if calibrated_probability >= self.critical_threshold:
            risk_level = "CRITICAL"
        elif calibrated_probability >= self.warning_threshold:
            risk_level = "WARNING"
        else:
            risk_level = "NORMAL"

        return V2ShadowPredictionResponse(
            equipment_id=request.equipment_id,
            status="SCORED",
            raw_probability=raw_probability,
            calibrated_probability=calibrated_probability,
            risk_score=round(calibrated_probability * 100, 2),
            risk_level=risk_level,
            model_version=self.model_version,
            shadow_only=shadow_only,
            feature_count=len(self.features),
            history_rows=len(contiguous),
        )

    @staticmethod
    def _latest_contiguous_segment(rows: pd.DataFrame) -> pd.DataFrame:
        if rows.empty:
            return rows
        gap = rows["collected_at"].diff()
        segment = gap.ne(pd.Timedelta(hours=1)).cumsum()
        return rows.loc[segment.eq(segment.iloc[-1])].copy()

    def _build_feature_frame(
        self, window: pd.DataFrame, equipment_id: str
    ) -> pd.DataFrame:
        latest = window.iloc[-1]
        core_values: dict[str, float] = {
            feature: float(latest[feature])
            for feature in CORE_FEATURES
            if feature in latest.index
        }
        for output, (source, operation) in ROLLING_DEFINITIONS.items():
            values = window[source]
            value = getattr(values, operation)()
            core_values[output] = float(value)

        if set(core_values) != set(CORE_FEATURES):
            missing = sorted(set(CORE_FEATURES) - set(core_values))
            raise ValueError(f"v2 핵심 특징 생성 실패: {missing}")

        baseline = self.baselines.loc[equipment_id]
        robust_values = {}
        for feature in CORE_FEATURES:
            scale = float(baseline[f"{feature}__scale"])
            if not np.isfinite(scale) or scale <= 0:
                raise ValueError(f"유효하지 않은 장비 기준선 scale: {feature}")
            robust_values[f"{feature}__robust_z"] = (
                core_values[feature]
                - float(baseline[f"{feature}__median"])
            ) / scale
        if not np.isfinite(list(robust_values.values())).all():
            raise ValueError("v2 온라인 특징에 비정상 값이 있습니다.")
        return pd.DataFrame([robust_values], columns=self.features)

    def _not_scored(
        self,
        equipment_id: str,
        status: str,
        history_rows: int,
        message: str,
        shadow_only: bool,
    ) -> V2ShadowPredictionResponse:
        return V2ShadowPredictionResponse(
            equipment_id=equipment_id,
            status=status,
            model_version=self.model_version,
            shadow_only=shadow_only,
            feature_count=len(self.features),
            history_rows=history_rows,
            message=message,
        )
