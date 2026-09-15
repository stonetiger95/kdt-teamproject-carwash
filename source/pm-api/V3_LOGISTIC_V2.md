# 예지보전 v3 Logistic v2 개발 안내

## 목적

기존 운영 모델 `logistic-platt-v1`을 바로 덮어쓰지 않고, v3 전처리 결과로
13개 robust 특징과 71개 전체 특징을 동일 조건에서 비교해 검토 가능한 후보
번들을 생성한다.

## 전체 구조

```text
v3 원본 CSV
  -> v3_preprocessing.py (시간 정렬, 구간 분리, 과거 rolling)
  -> 13개 특징의 최초 168시간 time-safe 기준선 보정
  -> 동일 57,288행 / 동일 장비 5-fold 비교
  -> 내부 장비 fold에서 L2 C 선택
  -> 1~3월 OOF 확률 보정·임계값 고정
  -> 4월 홀드아웃 검증
  -> candidate joblib + 모델 카드 + 검토 체크리스트
```

## 실행

프로젝트의 `pm-api` 디렉터리에서 아래 검증 실행점을 사용한다.

```powershell
python -u scripts\train_v3_logistic_v2_verified.py
```

결과는 다음 디렉터리에 생성된다.

```text
outputs/13_logistic_v2_candidate
```

## 주요 소스

- `app/v3_model_training.py`: 장비 중첩검증, 시간 홀드아웃, 보정, 임계값
- `app/v3_safe_baselines.py`: 최초 168시간만 이용한 robust 기준선 보정
- `app/v3_windows_estimator.py`: 현재 Windows sklearn 환경의 연속 메모리 계약
- `scripts/train_v3_logistic_v2.py`: 공통 학습·패키징 흐름
- `scripts/train_v3_logistic_v2_verified.py`: 현재 PC의 정식 실행점
- `scripts/finalize_v3_logistic_v2_candidate.py`: 문서·해시만 재생성

## 운영 안전 규칙

- 학습 스크립트는 `pm-api/artifacts`를 수정하지 않는다.
- 번들 상태가 `candidate_pending_user_review_and_shadow`이면 운영 연결 금지다.

## Shadow API

- 경로: POST /api/predictive-maintenance/predict-v2-shadow
- 입력: 동일 장비의 시간순 원시 센서 로그(최대 1,000행)
- 점수 조건: 최신 24개 행이 1시간 간격으로 연속이고 장비 기준선이 존재
- 보류 상태: WARMING_UP, UNKNOWN_DEVICE
- 안전 표지: 모든 응답의 shadow_only=true
- 운영 v1의 /predict 경로와 아티팩트는 변경하지 않는다.
- `REVIEW_REQUIRED.md` 승인과 실제 데이터 shadow 검증 후 별도 배포한다.
- 신규 장비는 168시간 기준선 정책을 정하기 전 자동 판정을 시작하지 않는다.

## 환경 호환 메모

현재 `plate310` 환경에서는 비연속 DataFrame slice의 대량 BLAS 예측이 DLL
수준으로 종료됐다. 검증 estimator는 입력을 C-contiguous float64로 복사하고,
로지스틱 선형식을 요소별 곱과 합으로 계산한다. 수학식은 동일하며 이 동작은
joblib 파이프라인에 포함되어 재로딩 뒤에도 유지된다.
