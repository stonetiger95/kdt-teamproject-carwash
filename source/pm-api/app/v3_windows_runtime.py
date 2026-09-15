"""Windows runtime compatibility helpers embedded in the v3 model pipeline."""

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
    """Copy dataframe/slice input to the stable native-memory contract.

    The verified Windows sklearn build can terminate at DLL level when a
    non-contiguous dataframe slice reaches repeated native solver calls.
    Keeping this named function in the serialized pipeline also protects
    inference after the joblib artifact is loaded in another process.
    """

    return np.ascontiguousarray(np.asarray(values, dtype=np.float64))


def make_windows_safe_logistic_pipeline(c_value: float) -> Pipeline:
    """Build the L2 SAGA pipeline with an explicit contiguous input contract."""

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


def fit_windows_safe_calibrator(
    method: str, raw_probability: np.ndarray, y_true: np.ndarray
) -> Any:
    """Fit the requested calibrator without the failing BLAS solver path."""

    raw = as_contiguous_float64(raw_probability).reshape(-1)
    target = np.ascontiguousarray(np.asarray(y_true, dtype=np.int32))
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
        return model.fit(
            as_contiguous_float64(training._logit(raw)), target
        )
    if method == "isotonic":
        model = IsotonicRegression(out_of_bounds="clip")
        return model.fit(raw, target)
    raise ValueError(f"unsupported calibration method: {method}")

