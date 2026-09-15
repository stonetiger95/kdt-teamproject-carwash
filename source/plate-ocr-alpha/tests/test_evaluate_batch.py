import tempfile
import unittest
from pathlib import Path

from evaluate_batch import build_summary, parse_ground_truth, prediction_row, select_sample


class EvaluateBatchTest(unittest.TestCase):
    def test_parse_ground_truth_supports_suffixes_and_rejects_unknown_names(self):
        self.assertEqual(parse_ground_truth(Path("01가1134-2.jpg")), "01가1134")
        self.assertEqual(parse_ground_truth(Path("123나4567.jpeg")), "123나4567")
        self.assertIsNone(parse_ground_truth(Path("unknown.jpeg")))

    def test_select_sample_is_reproducible(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = [root / f"{index:02d}가1234.jpg" for index in range(10)]
            self.assertEqual(select_sample(paths, 4, 7), select_sample(reversed(paths), 4, 7))

    def test_summary_compares_fixed_and_sigma_policies(self):
        predictions = [
            {"success": True, "detectedPlateNumber": "12가3456",
             "rawDetectedPlateNumber": "12가3456", "yoloConfidence": 0.95,
             "ocrConfidence": 0.96, "bbox": {"x1": 1, "y1": 2, "x2": 3, "y2": 4}},
            {"success": True, "detectedPlateNumber": "34나5678",
             "rawDetectedPlateNumber": "34나5678", "yoloConfidence": 0.70,
             "ocrConfidence": 0.75, "bbox": {"x1": 1, "y1": 2, "x2": 3, "y2": 4}},
            {"success": False, "message": "not detected"},
        ]
        names = ["12가3456.jpg", "99다0000.jpg", "88라1111.jpg"]
        rows = [prediction_row(sample_index=index, image_path=Path(name),
                               prediction=prediction, elapsed_ms=10)
                for index, (name, prediction) in enumerate(zip(names, predictions), start=1)]
        summary = build_summary(rows, seed=1)
        self.assertEqual(summary["sample_count"], 3)
        self.assertEqual(summary["exact_match_count"], 1)
        self.assertEqual(summary["recognition_error_count"], 2)
        self.assertEqual(summary["policies"]["current_fixed_0_8"]["review_count"], 2)
        self.assertEqual(summary["policies"]["current_fixed_0_8"]["captured_error_count"], 2)


if __name__ == "__main__":
    unittest.main()
