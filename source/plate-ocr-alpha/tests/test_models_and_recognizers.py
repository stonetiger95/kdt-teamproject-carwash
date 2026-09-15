import unittest
from unittest import mock

import numpy as np
import torch

from models.legacy_plate_cnn_ctc import LegacyPlateCNNCTC
from models.plate_cnn_ctc import PlateCNNCTCBase, PlateCNNCTCLite
from recognizers.lite050_recognizer import (
    Lite050Recognizer,
    ctc_prefix_beam_search,
)
from recognizers.plate_cnn_ctc_recognizer import (
    PlateCNNCTCRecognizer,
)


class RecognizerPublicMethodTest(unittest.TestCase):

    def test_plate_cnn_ctc_recognize_runs_preprocess_model_and_decode(self):
        recognizer = PlateCNNCTCRecognizer.__new__(
            PlateCNNCTCRecognizer
        )
        tensor = torch.zeros((1, 1, 48, 192))
        logits = torch.zeros((2, 1, 3))
        recognizer._preprocess = mock.Mock(return_value=tensor)
        recognizer.model = mock.Mock(return_value=logits)
        recognizer._decode_with_confidence = mock.Mock(
            return_value=("12가3456", 0.85)
        )
        image = np.zeros((20, 80, 3), dtype=np.uint8)

        result = PlateCNNCTCRecognizer.recognize(
            recognizer,
            image,
        )

        self.assertEqual(result, ("12가3456", 0.85))
        recognizer._preprocess.assert_called_once_with(image)
        recognizer.model.assert_called_once_with(tensor)
        recognizer._decode_with_confidence.assert_called_once_with(
            logits
        )

    def test_lite050_recognize_prefers_valid_plate_candidate(self):
        recognizer = Lite050Recognizer.__new__(Lite050Recognizer)
        recognizer.characters = ["1", "2", "가"]
        recognizer.blank_idx = 3
        tensor = torch.zeros((1, 1, 48, 192))
        recognizer._preprocess = mock.Mock(return_value=tensor)
        recognizer.model = mock.Mock(
            return_value=torch.zeros((2, 1, 4))
        )
        image = np.zeros((20, 80, 3), dtype=np.uint8)

        with mock.patch(
            "recognizers.lite050_recognizer."
            "ctc_prefix_beam_search",
            return_value=[
                ("invalid", -0.1),
                ("12가3456", -0.7),
            ],
        ):
            text, confidence = Lite050Recognizer.recognize(
                recognizer,
                image,
            )

        self.assertEqual(text, "12가3456")
        self.assertGreater(confidence, 0.0)
        self.assertLessEqual(confidence, 1.0)

    def test_ctc_prefix_beam_search_returns_ranked_text(self):
        log_probs = np.log(np.array([
            [0.8, 0.1, 0.1],
            [0.1, 0.1, 0.8],
            [0.1, 0.8, 0.1],
        ]))

        result = ctc_prefix_beam_search(
            log_probs,
            characters=["1", "2"],
            blank_index=2,
            beam_width=5,
        )

        self.assertEqual(result[0][0], "12")
        self.assertGreaterEqual(len(result), 2)


class ModelForwardMethodTest(unittest.TestCase):

    def test_legacy_model_forward_returns_class_sequence(self):
        model = LegacyPlateCNNCTC(num_classes=5).eval()
        images = torch.zeros((1, 3, 24, 94))

        logits = model.forward(images)

        self.assertEqual(logits.shape, (1, 5, 23))

    def test_base_model_forward_returns_ctc_layout(self):
        model = PlateCNNCTCBase(
            num_classes=6,
            sequence_length=8,
        ).eval()
        images = torch.zeros((1, 1, 48, 192))

        logits = model.forward(images)

        self.assertEqual(logits.shape, (8, 1, 6))

    def test_lite_model_forward_returns_ctc_layout(self):
        model = PlateCNNCTCLite(
            num_classes=6,
            width_multiplier=0.25,
            sequence_length=8,
        ).eval()
        images = torch.zeros((1, 1, 48, 192))

        logits = model.forward(images)

        self.assertEqual(logits.shape, (8, 1, 6))


if __name__ == "__main__":
    unittest.main()
