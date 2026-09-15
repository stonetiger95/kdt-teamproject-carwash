import unittest
from unittest import mock

import numpy as np
import torch

from utils.decoder import (
    greedy_decode,
    greedy_decode_batch,
    greedy_decode_with_confidence,
)
from utils.plate_postprocess import (
    is_valid_plate,
    levenshtein_distance,
    normalize_plate,
    plate_error_type,
    plate_similarity,
    postprocess_plate,
    repair_plate_candidate,
)
from utils.preprocess import (
    apply_plate_preprocess,
    ensure_bgr_3ch,
    preprocess_plate,
    read_image_unicode,
)


class PlatePostprocessFunctionTest(unittest.TestCase):

    def test_normalize_plate_removes_separators_and_symbols(self):
        self.assertEqual(
            normalize_plate(" ab-12가 3456! "),
            "AB12가3456",
        )

    def test_is_valid_plate_accepts_korean_standard_format(self):
        self.assertTrue(is_valid_plate("12가3456"))
        self.assertFalse(is_valid_plate("12-3456"))

    def test_repair_plate_candidate_extracts_valid_plate(self):
        self.assertEqual(
            repair_plate_candidate("x0012가003456"),
            "012가3456",
        )

    def test_postprocess_plate_returns_repaired_candidate(self):
        self.assertEqual(postprocess_plate(" 12가-3456 "), "12가3456")

    def test_plate_error_type_classifies_exact_match(self):
        self.assertEqual(
            plate_error_type("12가3456", "12가3456"),
            "exact_match",
        )

    def test_levenshtein_distance_counts_single_substitution(self):
        self.assertEqual(levenshtein_distance("1234", "1235"), 1)

    def test_plate_similarity_normalizes_edit_distance(self):
        self.assertAlmostEqual(
            plate_similarity("12가3456", "12가3457"),
            6 / 7,
        )


class PreprocessFunctionTest(unittest.TestCase):

    def test_read_image_unicode_decodes_numpy_bytes(self):
        expected = np.zeros((2, 3, 3), dtype=np.uint8)
        with mock.patch(
            "utils.preprocess.np.fromfile",
            return_value=np.array([1, 2, 3], dtype=np.uint8),
        ) as fromfile, mock.patch(
            "utils.preprocess.cv2.imdecode",
            return_value=expected,
        ) as imdecode:
            result = read_image_unicode("한글.jpg")

        self.assertIs(result, expected)
        fromfile.assert_called_once_with(
            "한글.jpg",
            dtype=np.uint8,
        )
        imdecode.assert_called_once()

    def test_ensure_bgr_3ch_converts_grayscale(self):
        gray = np.zeros((4, 5), dtype=np.uint8)

        result = ensure_bgr_3ch(gray)

        self.assertEqual(result.shape, (4, 5, 3))

    def test_apply_plate_preprocess_original_returns_same_array(self):
        image = np.zeros((4, 5, 3), dtype=np.uint8)

        result = apply_plate_preprocess(image, "original")

        self.assertIs(result, image)

    def test_preprocess_plate_returns_normalized_batched_tensor(self):
        image = np.full((4, 8, 3), 255, dtype=np.uint8)

        tensor = preprocess_plate(
            image,
            img_size=(6, 5),
            mode="original",
        )

        self.assertEqual(tuple(tensor.shape), (1, 3, 5, 6))
        self.assertEqual(tensor.dtype, torch.float32)
        self.assertTrue(torch.allclose(tensor, torch.ones_like(tensor)))


class DecoderFunctionTest(unittest.TestCase):

    def test_greedy_decode_collapses_repeats_and_blank(self):
        logits = self._logits([0, 0, 2, 1])

        result = greedy_decode(
            logits,
            idx_to_char={0: "A", 1: "B"},
            blank_idx=2,
        )

        self.assertEqual(result, "AB")

    def test_greedy_decode_with_confidence_returns_text_and_score(self):
        logits = self._logits([0, 2, 1])

        text, confidence = greedy_decode_with_confidence(
            logits,
            idx_to_char={0: "A", 1: "B"},
            blank_idx=2,
        )

        self.assertEqual(text, "AB")
        self.assertGreater(confidence, 0.99)

    def test_greedy_decode_batch_decodes_every_sequence(self):
        first = self._logits([0, 2, 1])[0]
        second = self._logits([1, 1, 2])[0]
        logits = torch.stack([first, second])

        result = greedy_decode_batch(
            logits,
            idx_to_char={0: "A", 1: "B"},
            blank_idx=2,
        )

        self.assertEqual(result, ["AB", "B"])

    def _logits(self, indices):
        logits = torch.full((1, 3, len(indices)), -10.0)
        for timestep, index in enumerate(indices):
            logits[0, index, timestep] = 10.0
        return logits


if __name__ == "__main__":
    unittest.main()
