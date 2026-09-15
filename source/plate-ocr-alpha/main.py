from pathlib import Path
import sys

from fastapi import FastAPI, UploadFile, File
import numpy as np 
import cv2
import traceback

project_root = Path(__file__).resolve().parent.parent
pm_api_root = project_root / "pm-api"

if str(pm_api_root) not in sys.path:
    sys.path.insert(0, str(pm_api_root))

# Load the Parquet runtime before PyTorch on Windows to avoid a native DLL conflict.
from app.main import router as predictive_maintenance_router
from infer import PlateRecognizer


app = FastAPI(
    title="Integrated AI API",
    version="1.0.0",
    description="번호판 인식과 예지보전 모델을 제공하는 통합 API 서버",
)

recognizer = PlateRecognizer(crop_padding_ratio=0.12)

app.include_router(
    predictive_maintenance_router,
    tags=["predictive-maintenance"],
)

@app.get("/")
def helth_check():
    return {
        "status" : "ok",
        "message" : "통합 AI API 작동",
        "services": ["plate-recognition", "predictive-maintenance"],
        "ocrModelVersion": recognizer.ocr_model_version,
        "cropPaddingRatio": recognizer.crop_padding_ratio,
    
    }
    
@app.post("/api/plate-recognition")
async def recognize_plate(
    image : UploadFile = File(...),
    ):
    
    try:
        contents = await image.read()
        
        if not contents:
            return {
                "success" : False,
                "plateNumber" : None,
                "message" : "업로드 된 파일이 비어있습니다."
            }
        np_arr = np.frombuffer(contents, np.uint8)
        
        img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        
        if img is None:
            return {
                "detectedPlateNumber" : None,
                "confidence" : None,
                "success" : False,
                "message" : "이미지를 읽을 수 없습니다."
            }
        
        result = recognizer.predict(img)
        
        return result
        
    except Exception as e:
        traceback.print_exc()

        return {
            "success": False,
            "plateNumber": None,
            "message": str(e)
        }    
