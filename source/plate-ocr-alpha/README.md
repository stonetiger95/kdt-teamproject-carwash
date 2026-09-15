# Plate OCR Alpha Server

팀 배포용 통합 AI 서버입니다. 하나의 FastAPI 프로세스에서 번호판 인식과 예지보전 모델을 함께 제공합니다. 번호판 인식의 최종 기준 모델 조합은 다음과 같습니다.

- 번호판 검출: YOLO11s (`weights/best.pt`)
- 문자 인식: Lite050 (`weights/lite050_best.pt`)
- OCR 디코더: CTC beam search 5 + 번호판 형식 우선 선택

Lite050은 2026-07-21 GPU locked final 1,000장 평가에서 번호판 완전 일치 정확도 97.1%를 기록한 체크포인트입니다.

## 폴더 구성

```text
plate-ocr-alpha/
  main.py
  infer.py
  requirements.txt
  models/
    plate_cnn_ctc.py
  recognizers/
    lite050_recognizer.py
  utils/
  weights/
    best.pt
    lite050_best.pt
```

두 가중치가 프로젝트 안에 포함되어 있으므로 학습 프로젝트나 외부 절대 경로가 필요하지 않습니다. 어느 위치에서 서버를 실행해도 `infer.py`가 이 폴더를 기준으로 가중치를 찾습니다.

## 설치

Python 3.10을 권장합니다.

```powershell
cd plate-ocr-alpha
py -3.10 -m venv .venv
.\.venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

## 실행

```powershell
conda activate plate310
# (.venv)가 같이 있을 시  
deactivate 
# 서버 구동
uvicorn main:app --host 0.0.0.0 --port 8000
```

- 상태 확인: `http://localhost:8000/`
- 예지보전 상태: `http://localhost:8000/health`
- API 문서: `http://localhost:8000/docs`
- 인식 API: `POST /api/plate-recognition`
- 예지보전 API: `POST /api/predictive-maintenance/predict`

별도의 8001번 예지보전 서버는 실행하지 않습니다. 프로젝트 루트의 `pm-api` 폴더는 모델 코드와 아티팩트 저장소로 사용되며 통합 서버가 시작할 때 자동으로 불러옵니다.

```powershell
curl -X POST "http://localhost:8000/api/plate-recognition" -F "image=@path\to\car.jpg"
```

정상 응답의 `ocrModelVersion` 값은 `lite050-beam5-format-final-gpu-20260721`입니다.

## 배포 전 확인

```powershell
python -m unittest discover -s tests -v
```

가중치 SHA-256:

- YOLO11s: `EF46F4543B221AD2AF673D494AC52FA2455C5F501E0C54343FA0500BEFCD2EBC`
- Lite050: `9FC1C5D57FA161C8F461CDDE94B389D4312DC5D95EE7DE8A3B81D361EC5B826C`
