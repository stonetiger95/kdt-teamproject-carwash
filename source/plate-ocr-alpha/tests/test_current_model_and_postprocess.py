import math
import unittest
from unittest.mock import MagicMock, patch

import cv2
import numpy as np
import torch

from models.plate_cnn_ctc import PlateCNNCTCLite
from recognizers.lite050_recognizer import Lite050Recognizer, ctc_prefix_beam_search
from utils.plate_postprocess import (
    is_valid_plate,
    levenshtein_distance,
    normalize_plate,
    plate_error_type,
    plate_similarity,
    postprocess_plate,
    repair_plate_candidate,
)


class CurrentModelAndPostprocessTest(unittest.TestCase):
    def test_lite_preprocess_supports_gray_bgr_bgra_and_rejects_empty(self):
        recognizer = Lite050Recognizer.__new__(Lite050Recognizer)
        recognizer.device = torch.device("cpu")
        images = [
            np.zeros((30, 80), dtype=np.uint8),
            np.zeros((30, 80, 3), dtype=np.uint8),
            np.zeros((30, 80, 4), dtype=np.uint8),
        ]
        for image in images:
            tensor = recognizer._preprocess(image)
            self.assertEqual(tuple(tensor.shape), (1, 1, 48, 192))
            self.assertEqual(tensor.dtype, torch.float32)
        with self.assertRaises(ValueError):
            recognizer._preprocess(np.array([], dtype=np.uint8))

    def test_ctc_prefix_beam_search_validates_shape_and_collapses_ctc_paths(self):
        with self.assertRaises(ValueError):
            ctc_prefix_beam_search(np.zeros((1, 2, 3)),
                                   characters=["1", "2"], blank_index=2, beam_width=3)
        probabilities = np.array([
            [0.80, 0.10, 0.10],
            [0.10, 0.10, 0.80],
            [0.10, 0.80, 0.10],
        ], dtype=np.float64)
        candidates = ctc_prefix_beam_search(np.log(probabilities),
                                            characters=["1", "2"],
                                            blank_index=2, beam_width=5)
        self.assertEqual(candidates[0][0], "12")
        self.assertGreaterEqual(candidates[0][1], candidates[-1][1])

    def test_lite_recognize_prefers_first_valid_plate_candidate(self):
        recognizer = Lite050Recognizer.__new__(Lite050Recognizer)
        recognizer.device = torch.device("cpu")
        recognizer.characters = ["1", "2", "가", "3", "4", "5", "6"]
        recognizer.blank_idx = 7
        recognizer.model = MagicMock(return_value=torch.zeros((1, 8, 4)))
        recognizer._preprocess = MagicMock(return_value=torch.zeros((1, 1, 48, 192)))
        candidates = [("INVALID", -0.1), ("12가3456", -0.7)]
        with patch("recognizers.lite050_recognizer.ctc_prefix_beam_search",
                   return_value=candidates):
            text, confidence = recognizer.recognize(np.zeros((48, 192), dtype=np.uint8))
        self.assertEqual(text, "12가3456")
        self.assertAlmostEqual(confidence, math.exp(-0.7 / 7), places=6)

    def test_lite_model_forward_returns_ctc_layout(self):
        model = PlateCNNCTCLite(num_classes=8, width_multiplier=0.25, sequence_length=12)
        model.eval()
        with torch.inference_mode():
            output = model(torch.zeros((2, 1, 48, 192)))
        self.assertEqual(tuple(output.shape), (12, 2, 8))

    def test_plate_normalization_and_validation(self):
        self.assertEqual(normalize_plate(" 12가-3456 "), "12가3456")
        self.assertEqual(normalize_plate(None), "")
        self.assertTrue(is_valid_plate("12가3456"))
        self.assertTrue(is_valid_plate("123가4567"))
        self.assertFalse(is_valid_plate("1가2345"))

    def test_plate_repair_and_postprocess_extract_valid_candidate(self):
        self.assertEqual(repair_plate_candidate("xx12가3456yy"), "12가3456")
        self.assertEqual(postprocess_plate(" 123가-4567 "), "123가4567")

    def test_plate_error_type_covers_primary_outcomes(self):
        self.assertEqual(plate_error_type("12가3456", ""), "empty_prediction")
        self.assertEqual(plate_error_type("12가3456", "12가3456"), "exact_match")
        self.assertEqual(plate_error_type("12가3456", "123456"), "missing_hangul")
        self.assertEqual(plate_error_type("12가3456", "34나5678"), "valid_format_mismatch")

    def test_edit_distance_and_similarity_handle_boundaries(self):
        self.assertEqual(levenshtein_distance("", "abc"), 3)
        self.assertEqual(levenshtein_distance("12가3456", "12가3457"), 1)
        self.assertEqual(plate_similarity("", ""), 1.0)
        self.assertAlmostEqual(plate_similarity("12가3456", "12가3457"), 6 / 7)


if __name__ == "__main__":
    unittest.main()
