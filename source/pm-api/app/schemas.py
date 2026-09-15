from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class PredictionRequest(BaseModel):
    equipment_id: str = Field(min_length=1)

    signal_strength_dbm: float
    success_rate: float = Field(ge=0, le=100)
    response_time_ms: float = Field(ge=0)

    retry_count: int = Field(ge=0)
    disconnect_count: int = Field(ge=0)

    packet_loss_rate: float = Field(ge=0, le=100)
    error_count: int = Field(ge=0)


class PredictionResponse(BaseModel):
    equipment_id: str
    raw_probability: float
    calibrated_probability: float
    risk_score: float
    risk_level: Literal["NORMAL", "WARNING", "CRITICAL"]
    model_version: str


class V2SensorReading(BaseModel):
    """One hourly input row used to build the v2 rolling feature snapshot."""

    equipment_id: str = Field(min_length=1)
    collected_at: datetime
    traffic_load: float
    temperature_c: float
    voltage_v: float
    signal_strength_dbm: float
    success_rate: float = Field(ge=0, le=100)
    response_time_ms: float = Field(ge=0)
    retry_count: int = Field(ge=0)
    disconnect_count: int = Field(ge=0)
    packet_loss_rate: float = Field(ge=0, le=100)
    error_count: int = Field(ge=0)
    days_since_maintenance: int = Field(ge=0)


class V2ShadowPredictionRequest(BaseModel):
    equipment_id: str = Field(min_length=1)
    readings: list[V2SensorReading] = Field(min_length=1, max_length=1000)


class V2ShadowPredictionResponse(BaseModel):
    equipment_id: str
    status: Literal["SCORED", "WARMING_UP", "UNKNOWN_DEVICE"]
    raw_probability: float | None = None
    calibrated_probability: float | None = None
    risk_score: float | None = None
    risk_level: Literal["NORMAL", "WARNING", "CRITICAL"] | None = None
    model_version: str
    # false means the backend may use this result as the primary decision.
    # true keeps the same score in observation-only comparison mode.
    shadow_only: bool = True
    feature_count: int = 13
    history_rows: int
    message: str | None = None
