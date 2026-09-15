"""Verified LogisticRegression compatibility layer for this Windows host.

Training delegates to sklearn's deterministic SAGA implementation.  Large
``predict_proba`` calls avoid the host's failing BLAS matrix-product path by
evaluating the exact linear equation with NumPy elementwise multiply-and-sum.
"""

from __future__ import annotations

from typing import Any

import numpy as np
from sklearn.impute import SimpleImputer
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer, StandardScaler

from app import v3_model_training as training


def as_contiguous_float64(values: Any) -> np.ndarray:
    """Return the explicit C-contiguous float64 model-input contract."""

    return np.ascontiguousarray(np.asarray(values, dtype=np.float64))


class WindowsSafeLogisticRegression(LogisticRegression):
    """LogisticRegression with a BLAS-independent probability calculation."""

    def decision_function(self, x: Any) -> np.ndarray:
        values = as_contiguous_float64(x)
        # Binary logistic regression has one coefficient row.  Elementwise
        # multiply-and-sum is algebraically identical to values @ coef_.T.
        return np.sum(values * self.coef_[0], axis=1) + self.intercept_[0]

    def predict_proba(self, x: Any) -> np.ndarray:
        decision = np.clip(self.decision_function(x), -709.0, 709.0)
        positive = 1.0 / (1.0 + np.exp(-decision))
        return np.column_stack((1.0 - positive, positive))


def make_verified_logistic_pipeline(c_value: float) -> Pipeline:
    """Build the serialized, Windows-safe L2 candidate pipeline."""

    return Pipeline(
        steps=[
            (
                "contiguous_float64",
                FunctionTransformer(as_contiguous_float64, validate=False),
            ),
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
            (
                "classifier",
                WindowsSafeLogisticRegression(
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


def fit_verified_calibrator(
    method: str, raw_probability: np.ndarray, y_true: np.ndarray
) -> Any:
    """Fit a calibrator using the same verified logistic implementation."""

    raw = as_contiguous_float64(raw_probability).reshape(-1)
    target = np.ascontiguousarray(np.asarray(y_true, dtype=np.int32))
    if method == "uncalibrated":
        return None
    if method == "platt":
        model = WindowsSafeLogisticRegression(
            C=1_000_000,
            solver="saga",
            max_iter=5_000,
            tol=1e-6,
            random_state=42,
            n_jobs=1,
        )
        return model.fit(
            as_contiguous_float64(training._logit(raw)), target
        )
    if method == "isotonic":
        model = IsotonicRegression(out_of_bounds="clip")
        return model.fit(raw, target)
    raise ValueError(f"unsupported calibration method: {method}")

