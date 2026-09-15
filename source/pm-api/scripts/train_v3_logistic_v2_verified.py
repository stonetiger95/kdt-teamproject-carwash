"""Verified v3 Logistic v2 training entry point for this workstation.

Usage:
    python -u scripts/train_v3_logistic_v2_verified.py
"""

from __future__ import annotations

from pathlib import Path
import runpy
import sys


PROJECT_DIR = Path(__file__).resolve().parents[1]
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

import app.v3_model_training as training
from app.v3_windows_estimator import (
    fit_verified_calibrator,
    make_verified_logistic_pipeline,
)


if __name__ == "__main__":
    training.make_logistic_pipeline = make_verified_logistic_pipeline
    training.fit_calibrator = fit_verified_calibrator
    runpy.run_path(
        str(Path(__file__).with_name("train_v3_logistic_v2.py")),
        run_name="__main__",
    )

