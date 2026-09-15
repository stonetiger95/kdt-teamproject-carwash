import asyncio
import importlib
import sys
import unittest
from types import SimpleNamespace
from unittest import mock

import numpy as np
import torch

import infer
from infer import PlateRecognizer


with mock.patch.object(infer, "PlateRecognizer") as recognizer_class:
    recognizer_class.return_value = SimpleNamespace(
        ocr_model_version="test-model",
        crop_padding_ratio=0.12,
    )
    sys.modules.pop("main", None)
    api_main = importlib.import_module("main")


class FastApiFunctionTest(unittest.TestCase):

    def test_helth_check_reports_active_model_configuration(self):
        api_main.recognizer = SimpleNamespace(
            ocr_model_version="lite-test",
            crop_padding_ratio=0.2,
        )

        result = api_main.helth_check()

        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["ocrModelVersion"], "lite-test")
        self.assertEqual(result["cropPaddingRatio"], 0.2)

    def test_recognize_plate_decodes_upload_and_returns_prediction(self):
        upload = mock.Mock()
        upload.read = mock.AsyncMock(return_value=b"encoded-image")
        image = np.zeros((8, 12, 3), dtype=np.uint8)
        prediction = {
            "success": True,
            "detectedPlateNumber": "12가3456",
        }
        api_main.recognizer = mock.Mock()
        api_main.recognizer.predict.return_value = prediction

        with mock.patch.object(
            api_main.cv2,
            "imdecode",
            return_value=image,
        ):
            result = asyncio.run(api_main.recognize_plate(upload))

        self.assertEqual(result, prediction)
        api_main.recognizer.predict.assert_called_once_with(image)


class PlateRecognizerFunctionTest(unittest.TestCase):

    def setUp(self):
        self.recognizer = PlateRecognizer.__new__(PlateRecognizer)
        self.recognizer.crop_padding_ratio = 0.1
        self.recognizer.yolo_confidence = 0.25
        self.recognizer.ocr_model_version = "test-version"

    def test_detect_plate_selects_highest_confidence_box(self):
        boxes = mock.MagicMock()
        boxes.__len__.return_value = 2
        boxes.conf = torch.tensor([0.25, 0.9])
        boxes.xyxy = torch.tensor([
            [1.0, 2.0, 10.0, 12.0],
            [20.0, 21.0, 40.0, 45.0],
        ])
        self.recognizer.yolo_model = mock.Mock()
        self.recognizer.yolo_model.predict.return_value = [
            SimpleNamespace(boxes=boxes)
        ]

        result = PlateRecognizer.detect_plate(
            self.recognizer,
            np.zeros((50, 60, 3), dtype=np.uint8),
        )

        self.assertEqual(result[:4], (20, 21, 40, 45))
        self.assertAlmostEqual(result[4], 0.9, places=5)

    def test_crop_plate_adds_padding_within_image_bounds(self):
        image = np.zeros((100, 200, 3), dtype=np.uint8)

        crop = PlateRecognizer.crop_plate(
            self.recognizer,
            image,
            (20, 20, 60, 40, 0.9),
        )

        self.assertEqual(crop.shape, (24, 48, 3))

    def test_recognize_text_delegates_to_ocr_model(self):
        crop = np.zeros((48, 192, 3), dtype=np.uint8)
        self.recognizer.ocr_model = mock.Mock()
        self.recognizer.ocr_model.recognize.return_value = (
            "12가3456",
            0.88,
        )

        result = PlateRecognizer.recognize_text(
            self.recognizer,
            crop,
        )

        self.assertEqual(result, ("12가3456", 0.88))
        self.recognizer.ocr_model.recognize.assert_called_once_with(crop)

    def test_predict_combines_detection_crop_ocr_and_postprocess(self):
        image = np.zeros((60, 100, 3), dtype=np.uint8)
        crop = np.zeros((20, 40, 3), dtype=np.uint8)
        self.recognizer.detect_plate = mock.Mock(
            return_value=(1, 2, 41, 22, 0.91)
        )
        self.recognizer.crop_plate = mock.Mock(return_value=crop)
        self.recognizer.recognize_text = mock.Mock(
            return_value=(" 12가-3456 ", 0.87)
        )

        result = PlateRecognizer.predict(self.recognizer, image)

        self.assertTrue(result["success"])
        self.assertEqual(result["detectedPlateNumber"], "12가3456")
        self.assertEqual(result["rawDetectedPlateNumber"], " 12가-3456 ")
        self.assertEqual(result["bbox"]["x2"], 41)
        self.assertEqual(result["ocrModelVersion"], "test-version")

    def test_test_reads_unicode_path_and_predicts(self):
        image = np.zeros((6, 10, 3), dtype=np.uint8)
        self.recognizer.predict = mock.Mock(
            return_value={"success": True}
        )

        with mock.patch.object(
            infer,
            "read_image_unicode",
            return_value=image,
        ) as image_reader:
            result = PlateRecognizer.test(
                self.recognizer,
                "한글/차량.jpg",
            )

        self.assertEqual(result, {"success": True})
        image_reader.assert_called_once_with("한글/차량.jpg")
        self.recognizer.predict.assert_called_once_with(image)


if __name__ == "__main__":
    unittest.main()
