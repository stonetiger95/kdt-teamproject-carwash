import asyncio
import importlib
import io
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import numpy as np
import torch

import infer


class CurrentApiAndInferTest(unittest.TestCase):
    def recognizer_without_weights(self):
        recognizer = infer.PlateRecognizer.__new__(infer.PlateRecognizer)
        recognizer.crop_padding_ratio = 0.1
        recognizer.yolo_confidence = 0.25
        recognizer.ocr_model_version = infer.PlateRecognizer.OCR_MODEL_VERSION
        return recognizer

    def test_detect_plate_handles_missing_boxes_and_selects_best_box(self):
        recognizer = self.recognizer_without_weights()
        recognizer.yolo_model = MagicMock()
        recognizer.yolo_model.predict.return_value = [SimpleNamespace(boxes=None)]
        self.assertIsNone(recognizer.detect_plate(np.zeros((20, 20, 3), dtype=np.uint8)))

        boxes = MagicMock()
        boxes.__len__.return_value = 2
        boxes.conf = torch.tensor([0.4, 0.9])
        boxes.xyxy = torch.tensor([[1, 2, 10, 12], [3, 4, 17, 19]])
        recognizer.yolo_model.predict.return_value = [SimpleNamespace(boxes=boxes)]
        detected = recognizer.detect_plate(np.zeros((20, 20, 3), dtype=np.uint8))
        self.assertEqual(detected[:4], (3, 4, 17, 19))
        self.assertAlmostEqual(detected[4], 0.9, places=6)

    def test_crop_plate_adds_padding_and_rejects_invalid_input(self):
        recognizer = self.recognizer_without_weights()
        image = np.zeros((20, 30, 3), dtype=np.uint8)
        crop = recognizer.crop_plate(image, (2, 2, 12, 12, 0.9))
        self.assertEqual(crop.shape, (12, 12, 3))
        with self.assertRaises(ValueError):
            recognizer.crop_plate(None, (0, 0, 1, 1, 0.9))
        with self.assertRaises(ValueError):
            recognizer.crop_plate(image, (40, 40, 50, 50, 0.9))

    def test_recognize_text_delegates_to_current_ocr_model(self):
        recognizer = self.recognizer_without_weights()
        recognizer.ocr_model = MagicMock()
        recognizer.ocr_model.recognize.return_value = ("12가3456", 0.88)
        crop = np.zeros((48, 192, 3), dtype=np.uint8)
        self.assertEqual(recognizer.recognize_text(crop), ("12가3456", 0.88))
        recognizer.ocr_model.recognize.assert_called_once_with(crop)

    def test_predict_combines_detection_crop_ocr_and_postprocess(self):
        recognizer = self.recognizer_without_weights()
        recognizer.detect_plate = MagicMock(return_value=(1, 2, 10, 12, 0.91))
        recognizer.crop_plate = MagicMock(return_value=np.zeros((10, 9, 3), dtype=np.uint8))
        recognizer.recognize_text = MagicMock(return_value=(" 12가-3456 ", 0.87))

        result = recognizer.predict(np.zeros((20, 20, 3), dtype=np.uint8))

        self.assertTrue(result["success"])
        self.assertEqual(result["detectedPlateNumber"], "12가3456")
        self.assertEqual(result["rawDetectedPlateNumber"], " 12가-3456 ")
        self.assertEqual(result["bbox"], {"x1": 1, "y1": 2, "x2": 10, "y2": 12})
        self.assertEqual(result["ocrModelVersion"], infer.PlateRecognizer.OCR_MODEL_VERSION)

    def test_predict_returns_failure_for_no_detection_and_internal_error(self):
        recognizer = self.recognizer_without_weights()
        recognizer.detect_plate = MagicMock(return_value=None)
        self.assertFalse(recognizer.predict(np.zeros((10, 10, 3), dtype=np.uint8))["success"])

        recognizer.detect_plate.side_effect = RuntimeError("model failed")
        result = recognizer.predict(np.zeros((10, 10, 3), dtype=np.uint8))
        self.assertFalse(result["success"])
        self.assertIn("model failed", result["message"])

    def load_main_with_mock_recognizer(self):
        sys.modules.pop("main", None)
        mocked = MagicMock()
        mocked.ocr_model_version = "test-model"
        mocked.crop_padding_ratio = 0.12
        with patch.object(infer, "PlateRecognizer", return_value=mocked):
            module = importlib.import_module("main")
        return module, mocked

    def test_health_check_reports_active_model_configuration(self):
        module, _ = self.load_main_with_mock_recognizer()
        result = module.helth_check()
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["ocrModelVersion"], "test-model")
        self.assertEqual(result["cropPaddingRatio"], 0.12)

    def test_recognize_plate_handles_empty_invalid_and_valid_upload(self):
        module, recognizer = self.load_main_with_mock_recognizer()

        class Upload:
            def __init__(self, data):
                self.data = data

            async def read(self):
                return self.data

        empty = asyncio.run(module.recognize_plate(Upload(b"")))
        self.assertFalse(empty["success"])

        with patch.object(module.cv2, "imdecode", return_value=None):
            invalid = asyncio.run(module.recognize_plate(Upload(b"bad")))
        self.assertFalse(invalid["success"])

        recognizer.predict.return_value = {"success": True, "detectedPlateNumber": "12가3456"}
        with patch.object(module.cv2, "imdecode",
                          return_value=np.zeros((10, 10, 3), dtype=np.uint8)):
            valid = asyncio.run(module.recognize_plate(Upload(b"image")))
        self.assertTrue(valid["success"])
        self.assertEqual(valid["detectedPlateNumber"], "12가3456")


if __name__ == "__main__":
    unittest.main()
