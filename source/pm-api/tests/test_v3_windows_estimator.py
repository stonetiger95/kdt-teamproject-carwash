from __future__ import annotations

import joblib
import numpy as np
import pandas as pd

from app.v3_windows_estimator import (
    WindowsSafeLogisticRegression,
    make_verified_logistic_pipeline,
)


def test_windows_safe_probability_matches_logistic_equation():
    rng = np.random.default_rng(42)
    features = rng.normal(size=(500, 4))
    target = (features[:, 0] - features[:, 1] > 0).astype(int)
    model = WindowsSafeLogisticRegression(
        solver="saga", max_iter=2_000, random_state=42
    ).fit(features, target)

    decision = np.sum(features * model.coef_[0], axis=1) + model.intercept_[0]
    expected = 1.0 / (1.0 + np.exp(-decision))

    np.testing.assert_allclose(model.predict_proba(features)[:, 1], expected)


def test_verified_pipeline_handles_sliced_dataframe_and_joblib_roundtrip(
    tmp_path,
):
    rng = np.random.default_rng(7)
    frame = pd.DataFrame(rng.normal(size=(2_000, 6)))
    target = (frame[0] + frame[2] > 0).astype(int)
    sliced = frame.iloc[::2]
    model = make_verified_logistic_pipeline(0.01).fit(
        sliced, target.loc[sliced.index]
    )
    before = model.predict_proba(frame.iloc[1::2])[:, 1]

    path = tmp_path / "candidate.joblib"
    joblib.dump(model, path)
    restored = joblib.load(path)
    after = restored.predict_proba(frame.iloc[1::2])[:, 1]

    np.testing.assert_allclose(before, after)

