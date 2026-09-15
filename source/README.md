# CLOUDWASH 공개용 소스 코드

이 디렉터리는 팀프로젝트 원본 저장소에서 포트폴리오 공개가 가능한 코드만 별도로 복사하고 정리한 버전이다. 원본 비공개 저장소의 Git 이력은 포함하지 않았으며, 학습 데이터·모델 가중치·운영 설정·로그·DB 덤프는 제외했다.

## 구성

| 디렉터리 | 역할 | 주요 기술 |
|---|---|---|
| `backend` | 회원·예약·결제·입출차 상태 및 AI 서비스 연동 | Java 17, Spring Boot, MyBatis, PostgreSQL |
| `frontend` | 고객·사업자·관리자 웹 화면 | Vue 3, Vite, Pinia, PrimeVue |
| `plate-ocr-alpha` | 번호판 탐지·OCR 추론 API | Python, FastAPI, PyTorch, Ultralytics YOLO |
| `pm-api` | 예지보전 추론 API와 평가 코드 | Python, FastAPI, scikit-learn |

## 공개본에서 제외한 항목

- AI Hub·Roboflow·광안대교 번호판 원본 이미지와 라벨
- 에스트래픽 실무 담당자가 제공한 프로젝트용 합성 센서 원본 데이터
- OCR·YOLO·예지보전 모델 가중치와 학습 산출물
- 실제 API 키, OAuth 비밀값, 결제 연동 정보, JWT 키, 메일 계정
- 사용자·차량·결제 정보가 들어갈 수 있는 로그, 업로드 파일, DB 덤프

따라서 이 공개본은 코드를 검토하기 위한 포트폴리오 자료이며, 비공개 자산 없이 전체 서비스를 즉시 재현하는 배포 패키지는 아니다.

## 로컬 확인

### 프런트엔드

1. `frontend/.env.example`을 `.env`로 복사한다.
2. 필요한 지도 서비스 키를 본인의 개발용 키로 교체한다.
3. `npm install` 후 `npm run dev`를 실행한다.

### 백엔드

백엔드는 PostgreSQL 접속 정보와 선택 기능별 외부 서비스 설정이 필요하다. 실제 설정 파일은 공개본에서 제외했으므로 본인의 로컬 `application.yaml`을 만들어야 한다. 소스 컴파일은 Windows에서 `gradlew.bat compileJava`로 확인할 수 있다.

### AI API

각 디렉터리의 `requirements.txt`로 별도 Python 환경을 구성한다. 번호판 OCR과 예지보전 추론에는 공개본에서 제외된 가중치가 필요하므로, 가중치 없이 API 전체 추론 결과를 재현할 수는 없다. 전처리·추론 흐름과 테스트·평가 코드는 검토할 수 있다.

## 주의

저장소에 값을 추가하기 전 최상위 `.gitignore`를 확인한다. `.env.example`에는 실제 키를 넣지 않으며, 모델이나 데이터 파일을 강제 추가하지 않는다.

