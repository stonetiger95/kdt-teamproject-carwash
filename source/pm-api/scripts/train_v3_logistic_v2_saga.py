"""Stable Windows entry point for the v3 Logistic v2 candidate.

The verified ``plate310`` environment terminates at native-DLL level when
medium-sized LogisticRegression jobs use liblinear/lbfgs.  SAGA supports the
same L2 objective and runs reliably in this environment.  This wrapper replaces
only the solver factory, then executes the fully documented training command.

Use this file as the canonical training entry point on the current workstation.
"""

from __future__ import annotations

from pathlib import Path
import runpy
import sys

from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler


PROJECT_DIR = Path(__file__).resolve().parents[1]
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

import app.v3_model_training as training


def make_stable_logistic_pipeline(c_value: float) -> Pipeline:
    """Return the deterministic SAGA implementation of the L2 model."""

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


if __name__ == "__main__":
    training.make_logistic_pipeline = make_stable_logistic_pipeline
    runpy.run_path(
        str(Path(__file__).with_name("train_v3_logistic_v2.py")),
        run_name="__main__",
    )

