# Predictive Maintenance API

> 통합 실행 시에는 `plate-ocr-alpha`의 FastAPI 서버가 이 모듈의 라우터를 포함합니다. 기본 운영 구성에서는 8001 서버를 별도로 실행하지 않고 `http://localhost:8000`을 사용합니다. 이 폴더의 단독 실행 방식은 모델 API 개발과 테스트 용도로 유지합니다.

PM_BUILD의 예지보전 모델을 서비스하기 위한 FastAPI 애플리케이션입니다.

## 1차 API

- `GET /health`
- `POST /api/predictive-maintenance/predict`

현재 최종 모델 입력 특성:

- `signal_strength_dbm`
- `success_rate`
- `response_time_ms`
- `retry_count`
- `disconnect_count`
- `packet_loss_rate`
- `error_count`

모델 artifact는 `artifacts/`에 배치합니다. 모델 저장 및 추론 코드는 PM_BUILD 검증이 끝난 뒤 연결합니다.

## 실행

```powershell
conda activate plate310
uvicorn app.main:app --reload --port 8001
```
