# utils/preprocess.py

import cv2
import numpy as np
import torch


def read_image_unicode(path):
    """
    한글 경로 이미지도 안정적으로 읽기 위한 함수
    """
    data = np.fromfile(path, dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)

    if img is None:
        raise FileNotFoundError(f"이미지를 읽을 수 없습니다: {path}")

    return img

def ensure_bgr_3ch(img):
    if len(img.shape) == 2:
        return cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    return img


def apply_plate_preprocess(img, mode="original"):
    if mode == "original":
        return img

    if mode == "clahe":
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        return cv2.cvtColor(clahe.apply(gray), cv2.COLOR_GRAY2BGR)

    if mode == "gray_resize3x_clahe":
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        h, w = gray.shape[:2]
        gray = cv2.resize(gray, (w * 3, h * 3), interpolation=cv2.INTER_CUBIC)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        return cv2.cvtColor(clahe.apply(gray), cv2.COLOR_GRAY2BGR)

    raise ValueError(f"Unknown preprocess mode: {mode}")

def preprocess_plate(img, img_size, mode="original"):
    img = apply_plate_preprocess(img, mode)
    img = ensure_bgr_3ch(img)

    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img = cv2.resize(img, img_size)

    img = img.astype(np.float32) / 255.0
    img = (img - 0.5) / 0.5

    img = np.transpose(img, (2, 0, 1))
    tensor = torch.tensor(img, dtype=torch.float32).unsqueeze(0)

    return tensor