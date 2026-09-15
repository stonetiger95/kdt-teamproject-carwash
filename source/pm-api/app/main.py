import logging

from fastapi import APIRouter, FastAPI, HTTPException

from .model_service import ModelService
from .schemas import (
    PredictionRequest,
    PredictionResponse,
    V2ShadowPredictionRequest,
    V2ShadowPredictionResponse,
)
from .v2_shadow_service import V2ShadowModelService


logger = logging.getLogger(__name__)

router = APIRouter()

app = FastAPI(
    title="Predictive Maintenance API",
    version="0.1.0",
)

model_service = ModelService()
v2_shadow_service = V2ShadowModelService()


@router.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "ok",
        "service": "predictive-maintenance-api",
        "modelLoaded": model_service.is_loaded,
        "modelVersion": model_service.model_version,
        "activeModel": "v2",
        "activeModelLoaded": v2_shadow_service.is_loaded,
        "activeModelVersion": v2_shadow_service.model_version,
        "v1FallbackModelVersion": model_service.model_version,
        "productionActivationAllowed": (
            v2_shadow_service.production_activation_allowed
        ),
    }


@router.post(
    "/api/predictive-maintenance/predict",
    response_model=PredictionResponse,
)
def predict(
    request: PredictionRequest,
) -> PredictionResponse:

    try:
        return model_service.predict(request)

    except Exception:
        logger.exception(
            "예측 처리 실패: equipment_id=%s",
            request.equipment_id,
        )
        raise HTTPException(
            status_code=500,
            detail="예측 처리 중 오류가 발생했습니다.",
        )


@router.post(
    "/api/predictive-maintenance/predict-v2-active",
    response_model=V2ShadowPredictionResponse,
)
def predict_v2_active(
    request: V2ShadowPredictionRequest,
) -> V2ShadowPredictionResponse:
    """Score the activated v2 model using its 24-hour input contract."""

    try:
        return v2_shadow_service.predict(request, shadow_only=False)
    except ValueError as exception:
        raise HTTPException(status_code=422, detail=str(exception))
    except Exception:
        logger.exception(
            "v2 active 예측 처리 실패: equipment_id=%s",
            request.equipment_id,
        )
        raise HTTPException(
            status_code=500,
            detail="v2 active 예측 처리 중 오류가 발생했습니다.",
        )


@router.post(
    "/api/predictive-maintenance/predict-v2-shadow",
    response_model=V2ShadowPredictionResponse,
)
def predict_v2_shadow(
    request: V2ShadowPredictionRequest,
) -> V2ShadowPredictionResponse:
    """Score v2 without changing the production v1 decision path."""

    try:
        return v2_shadow_service.predict(request, shadow_only=True)
    except ValueError as exception:
        raise HTTPException(status_code=422, detail=str(exception))
    except Exception:
        logger.exception(
            "v2 shadow 예측 처리 실패: equipment_id=%s",
            request.equipment_id,
        )
        raise HTTPException(
            status_code=500,
            detail="v2 shadow 예측 처리 중 오류가 발생했습니다.",
        )


app.include_router(router)
