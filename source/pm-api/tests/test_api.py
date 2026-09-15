from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


NORMAL_SAMPLE = {
    "equipment_id": "ANT-001",
    "signal_strength_dbm": 87.41,
    "success_rate": 99.31,
    "response_time_ms": 40.22,
    "retry_count": 0,
    "disconnect_count": 0,
    "packet_loss_rate": 0.136,
    "error_count": 0,
}


def test_health_reports_loaded_model():
    response = client.get("/health")

    assert response.status_code == 200

    result = response.json()
    assert result["status"] == "ok"
    assert result["modelLoaded"] is True
    assert result["modelVersion"] == "logistic-platt-v1"


def test_predict_returns_calibrated_risk():
    response = client.post(
        "/api/predictive-maintenance/predict",
        json=NORMAL_SAMPLE,
    )

    assert response.status_code == 200

    result = response.json()
    assert result["equipment_id"] == "ANT-001"
    assert 0 <= result["raw_probability"] <= 1
    assert 0 <= result["calibrated_probability"] <= 1
    assert 0 <= result["risk_score"] <= 100
    assert result["risk_level"] in {
        "NORMAL",
        "WARNING",
        "CRITICAL",
    }
    assert result["risk_score"] == round(
        result["calibrated_probability"] * 100,
        2,
    )


def test_predict_rejects_out_of_range_percentage():
    invalid_sample = {
        **NORMAL_SAMPLE,
        "success_rate": 100.01,
    }

    response = client.post(
        "/api/predictive-maintenance/predict",
        json=invalid_sample,
    )

    assert response.status_code == 422
