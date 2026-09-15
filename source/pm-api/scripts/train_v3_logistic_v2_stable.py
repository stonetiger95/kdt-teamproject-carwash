"""Canonical v3 training entry point for the current Windows environment.

Both the base estimator and Platt calibrator use deterministic SAGA here.  The
plate310 build has a native-DLL failure in liblinear/lbfgs on medium-to-large
fits; SAGA optimizes the same logistic objectives without that failing path.
"""

from __future__ import annotations

from pathlib import Path
import runpy
import sys
from typing import Any

import numpy as np
from sklearn.impute import SimpleImputer
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler


PROJECT_DIR = Path(__file__).resolve().parents[1]
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

import app.v3_model_training as training


def make_stable_logistic_pipeline(c_value: float) -> Pipeline:
    """Build the base L2 logistic model using the stable SAGA solver."""

    return Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
            (
                "classifier",
                LogisticRegression(
                    penalty="l2",
                    C=float(c_value),
                    class_weight="balanced",
                    solver="saga",
                    max_iter=5_000,
                    tol=1e-4,
                    random_state=42,
                    n_jobs=1,
                ),
            ),
        ]
    )


def fit_stable_calibrator(
    method: str, raw_probability: np.ndarray, y_true: np.ndarray
) -> Any:
    """Fit calibration while avoiding the failing lbfgs native path."""

    if method == "uncalibrated":
        return None
    if method == "platt":
        model = LogisticRegression(
            C=1_000_000,
            solver="saga",
            max_iter=5_000,
            tol=1e-6,
            random_state=42,
            n_jobs=1,
        )
        return model.fit(training._logit(raw_probability), y_true)
    if method == "isotonic":
        model = IsotonicRegression(out_of_bounds="clip")
        return model.fit(np.asarray(raw_probability), y_true)
    raise ValueError(f"unsupported calibration method: {method}")


if __name__ == "__main__":
    training.make_logistic_pipeline = make_stable_logistic_pipeline
    training.fit_calibrator = fit_stable_calibrator
    runpy.run_path(
        str(Path(__file__).with_name("train_v3_logistic_v2.py")),
        run_name="__main__",
    )

