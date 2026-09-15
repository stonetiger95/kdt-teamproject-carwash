import os
from pathlib import Path

import torch
import numpy as np


PROJECT_DIR = Path(__file__).resolve().parent
os.environ.setdefault("YOLO_CONFIG_DIR", str(PROJECT_DIR / ".ultralytics"))

from ultralytics import YOLO

from utils.preprocess import read_image_unicode
from utils.plate_postprocess import postprocess_plate
from recognizers.lite050_recognizer import Lite050Recognizer


class PlateRecognizer:
    OCR_MODEL_VERSION = "lite050-beam5-format-final-gpu-20260721"

    def __init__(
        self,
        yolo_weight_path=PROJECT_DIR / "weights" / "best.pt",
        ocr_weight_path=PROJECT_DIR / "weights" / "lite050_best.pt",
        yolo_confidence = 0.25,
        crop_padding_ratio = 0.12
    ):
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.yolo_confidence = yolo_confidence
        self.crop_padding_ratio = crop_padding_ratio
        self.ocr_model_version = self.OCR_MODEL_VERSION
        
        #YOLO 로드
        self.yolo_model = YOLO(yolo_weight_path)
        
        # Final locked evaluation winner: Lite050 + beam5 format decoding.
        self.ocr_model = Lite050Recognizer(
            weight_path=str(ocr_weight_path),
            device=self.device
        )
        
    def detect_plate(self, image):
        results = self.yolo_model.predict(
            source = image,
            conf = self.yolo_confidence,
            verbose = False
        )
        
        result = results[0]
        
        if result.boxes is None or len(result.boxes) ==0:
            return None
        
        boxes = result.boxes
        confs = boxes.conf.cpu().numpy()
        
        best_idx = int(np.argmax(confs))
        
        xyxy = boxes.xyxy[best_idx].cpu().numpy()
        
        x1, y1, x2, y2 = map(int , xyxy)
        
        confidence = float(confs[best_idx])
        
        return x1, y1, x2, y2, confidence
    
    def crop_plate(self, image, bbox):
        
        if image is None:
            raise ValueError("이미지를 읽을 수 없습니다.")
        
        h, w  = image.shape[:2]
        
        x1, y1, x2, y2, _ = bbox
        box_w = max(1, x2 - x1)
        box_h = max(1, y2 - y1)
        pad_x = int(box_w * self.crop_padding_ratio)
        pad_y = int(box_h * self.crop_padding_ratio)
        
        x1 = max(0, x1 - pad_x)
        y1 = max(0, y1 - pad_y)
        x2 = min(w, x2 + pad_x)
        y2 = min(h, y2 + pad_y)
        
        crop = image[y1:y2, x1:x2]
        
        if crop.size == 0 :
            raise ValueError("번호판 Crop 결과가 비어있습니다.")
        
        '''
        디버깅용 코드
        os.makedirs("temp", exist_ok=True)
        crop_path = "temp/plate_crop.jpg"
        cv2.imwrite(crop_path, crop)
        
        return crop_path
        '''
        
        return crop 
    def recognize_text(self, crop_img):
        return self.ocr_model.recognize(crop_img)
    
    def predict(self, image):
        try:
            bbox = self.detect_plate(image)
            
            if bbox is None:
                return {
                    "detectedPlateNumber": None,
                    "confidence": None,
                    "success": False,
                    "message": "번호판을 탐지하지 못했습니다."
                }
            crop_img = self.crop_plate(image, bbox)

        
            raw_plate_number, ocr_confidence = self.recognize_text(crop_img)
            plate_number = postprocess_plate(raw_plate_number)
            x1, y1, x2, y2, _ = bbox
            
            return {
                "detectedPlateNumber": plate_number,
                "rawDetectedPlateNumber": raw_plate_number,
                "yoloConfidence": bbox[4],
                "ocrConfidence": ocr_confidence,
                "bbox": {
                    "x1": x1,
                    "y1": y1,
                    "x2": x2,
                    "y2": y2,
                },
                "success": True,
                "message": "번호판 인식 성공",
                "ocrModelVersion": self.ocr_model_version,
            }
            
            
        except Exception as e:
            return {
                    "detectedPlateNumber": None,
                    "confidence": None,
                    "bbox": None,
                    "success": False,
                    "message": f"번호판 인식 오류: {str(e)}"
            }
            
    def test(self, image_path):
        """
        로컬 테스트용 함수
        파일 경로를 받아서 이미지 배열로 변환 후 predict_array 호출
        """

        image = read_image_unicode(image_path)

        if image is None:
            return {
                "detectedPlateNumber": None,
                "confidence": None,
                "success": False,
                "message": "이미지를 읽을 수 없습니다."
            }

        return self.predict(image)
            
        
if __name__ == "__main__":
    recognizer = PlateRecognizer()
    
    result = recognizer.test("file/test_car.jpg")
    
    print(result)
