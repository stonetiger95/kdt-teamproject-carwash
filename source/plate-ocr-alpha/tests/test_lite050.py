import unittest
from pathlib import Path

import numpy as np

from recognizers.lite050_recognizer import (
    PLATE_PATTERN,
    Lite050Recognizer,
    ctc_prefix_beam_search,
)


PROJECT_DIR = Path(__file__).resolve().parents[1]


class Lite050DecoderTest(unittest.TestCase):
    def test_final_checkpoint_loads_strictly(self):
        recognizer = Lite050Recognizer(
            str(PROJECT_DIR / "weights" / "lite050_best.pt"),
            "cpu",
        )
        parameter_count = sum(parameter.numel() for parameter in recognizer.model.parameters())
        self.assertEqual(parameter_count, 1_140_503)

    def test_beam_decoder_returns_ranked_candidates(self):
        log_probs = np.log(
            np.array(
                [
                    [0.80, 0.10, 0.10],
                    [0.10, 0.10, 0.80],
                    [0.10, 0.80, 0.10],
                ],
                dtype=np.float64,
            )
        )
        candidates = ctc_prefix_beam_search(
            log_probs,
            characters=["1", "2"],
            blank_index=2,
            beam_width=5,
        )
        self.assertEqual(candidates[0][0], "12")
        self.assertGreaterEqual(len(candidates), 2)

    def test_plate_pattern_accepts_standard_lengths(self):
        self.assertIsNotNone(PLATE_PATTERN.fullmatch("12가3456"))
        self.assertIsNotNone(PLATE_PATTERN.fullmatch("123가4567"))
        self.assertIsNone(PLATE_PATTERN.fullmatch("1가2345"))


if __name__ == "__main__":
    unittest.main()
