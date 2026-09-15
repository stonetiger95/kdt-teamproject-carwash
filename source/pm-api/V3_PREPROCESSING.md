# 예지보전 v3 전처리 파이프라인

## 목적

기존 v3 실험의 전처리 규칙은 여러 Jupyter Notebook에 분산되어 있었다.
이 문서와 `app/v3_preprocessing.py`는 학습과 추론이 같은 규칙을 사용하도록
1단계 데이터 계약을 코드로 고정한다.

현재 운영 모델 `logistic-platt-v1`과 `artifacts/` 파일은 변경하지 않는다.

## 실행

`pyarrow`가 설치된 `plate310` 환경을 사용한다.

```powershell
conda activate plate310
python scripts\build_v3_preprocessing.py
```

기본 입력과 출력은 다음과 같다.

```text
입력  data/predictive_maintenance_timeseries_synthetic_v3.csv
출력  outputs/12_reproducible_preprocessing
```

다른 파일을 검증하려면 `--input`과 `--output`을 지정한다. 표준 v3 행 수와
장비·사건 수가 다른 데이터는 기본적으로 거부한다. 의도적인 다음 버전 데이터만
`--allow-profile-change`를 사용하며, 스키마·라벨·누수 검사는 계속 적용된다.

## 처리 순서

1. 필수 컬럼, 자료형, 센서 범위, 장비·시각 중복을 검증한다.
2. 연속된 `failure_event` 행을 하나의 독립 고장 사건으로 묶는다.
3. 고장 시작부터 정비 직전까지와 정비 이벤트 행을 모델링 데이터에서 제거한다.
4. 실제 고장 시각으로 `failure_within_7d`를 재계산해 원본 라벨과 대조한다.
5. 한 시간이 아닌 관측 간격마다 새로운 장비 연속 구간을 만든다.
6. 지도학습용 원본·rolling·count 특징 71개를 생성한다.
7. 정상학습용 24시간 특징과 장비별 168행 기준선으로 robust 특징 13개를 생성한다.
8. 기존 실험과 같은 장비 단위 5-fold 분할을 재현한다.
9. 품질 보고서, 특징 계약, 파일 해시를 저장한다.

## 누수 방지 규칙

- `failure_within_7d`는 정답으로만 사용한다.
- `rul_hours`, `risk_score`, `risk_level`, 미래 고장 시각 파생값은 입력 특징에서 제외한다.
- rolling은 현재 행과 과거 행만 사용한다.
- 시간 공백, 고장, 정비로 나뉜 구간을 넘어 rolling을 계산하지 않는다.
- 새 장비의 정상학습 특징은 24시간 warm-up이 끝난 뒤 계산한다.
- 장비별 정상 기준선은 최초 유효 168행으로 고정한다.

## 산출물

| 파일 | 내용 |
|---|---|
| `v3_modeling_base.parquet` | 고장 후·정비 구간을 제거한 기준 데이터 |
| `v3_features_71.parquet` | 지도학습용 71개 특징 |
| `v3_features_13_robust.parquet` | OCSVM 보조모델용 장비 정규화 특징 |
| `failure_events.parquet` | 12개 독립 고장 사건 |
| `device_baselines.parquet` | 장비별 중앙값과 MAD 기반 scale |
| `device_fold_assignment.csv` | 재현 가능한 장비 단위 5-fold |
| `feature_manifest.json` | 특징 이름, 순서, 창 크기, 누수 금지 컬럼 |
| `data_quality_report.json` | 행·장비·고장·구간·검증 결과 |
| `artifact_manifest.json` | 입력과 모든 산출물의 SHA-256 |

## 테스트

```powershell
python -m pytest tests\test_v3_preprocessing.py -q
```

테스트는 미래 데이터 변경이 과거 특징을 바꾸지 않는지, 시간 공백에서 rolling이
초기화되는지, 온라인 참조 변환과 배치 변환이 같은지, 누수 컬럼이 특징에 포함되지
않는지, 장비 fold가 재현되는지를 확인한다.

## 다음 단계

이 파이프라인이 검증된 뒤 `v3_features_71.parquet`를 기준으로 Logistic L2 후보를
재학습하고 확률 보정과 Warning/Critical 임계값을 새로 선택한다. OCSVM은
`v3_features_13_robust.parquet`를 사용하는 보조 이상탐지 모델로 유지한다.
